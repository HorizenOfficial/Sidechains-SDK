package io.horizen.account.forger

import akka.util.Timeout
import io.horizen.SidechainTypes
import io.horizen.account.block.AccountBlock.calculateReceiptRoot
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.companion.SidechainAccountTransactionsCompanion
import io.horizen.account.fork.GasFeeFork
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.{AccountMemoryPool, MempoolMap, TransactionsByPriceAndNonceIter}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.PrivateKeySecp256k1
import io.horizen.account.state._
import io.horizen.account.state.receipt.EthereumConsensusDataReceipt
import io.horizen.account.storage.AccountHistoryStorage
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.FeeUtils.calculateBaseFee
import io.horizen.account.utils._
import io.horizen.account.wallet.AccountWallet
import io.horizen.block._
import io.horizen.consensus._
import io.horizen.evm.{Address, Hash}
import io.horizen.forge.{AbstractForgeMessageBuilder, ForgeFailure, ForgeSuccess, MainchainSynchronizer}
import io.horizen.params.NetworkParams
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.secret.{PrivateKey25519, Secret}
import io.horizen.transaction.TransactionSerializer
import io.horizen.utils.{
  ByteArrayWrapper,
  ClosableResourceHandler,
  DynamicTypedSerializer,
  ForgingStakeMerklePathInfo,
  ListSerializer,
  MerklePath,
  MerkleTree,
  TimeToEpochUtils,
  WithdrawalEpochInfo,
  WithdrawalEpochUtils
}
import io.horizen.vrf.VrfOutput
import sparkz.core.NodeViewModifier
import sparkz.core.block.Block.{BlockId, Timestamp}
import sparkz.util.{ModifierId, bytesToId}

import java.math.BigInteger
import java.util.{ArrayList => JArrayList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.SECONDS
import scala.util.{Failure, Success, Try}

class AccountForgeMessageBuilder(
    mainchainSynchronizer: MainchainSynchronizer,
    companion: SidechainAccountTransactionsCompanion,
    params: NetworkParams,
    allowNoWebsocketConnectionInRegtest: Boolean
) extends AbstractForgeMessageBuilder[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock](
      mainchainSynchronizer,
      companion,
      params,
      allowNoWebsocketConnectionInRegtest
    )
      with ClosableResourceHandler {
  type FPI = AccountFeePaymentsInfo
  type HSTOR = AccountHistoryStorage
  type VL = AccountWallet
  type HIS = AccountHistory
  type MS = AccountState
  type MP = AccountMemoryPool

  def computeBlockInfo(
      view: AccountStateView,
      sidechainTransactions: Iterable[SidechainTypes#SCAT],
      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
      blockContext: BlockContext,
      forgerAddress: AddressProposition,
      blockSize: Long
  ): (Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], AccountBlockFeeInfo) = {

    // we must ensure that all the tx we get from mempool are applicable to current state view
    // and we must stay below the block gas limit threshold, therefore we might have a subset of the input transactions

    val (receiptList, appliedTransactions, cumBaseFee, cumForgerTips) =
      tryApplyAndGetBlockInfo(view, mainchainBlockReferencesData, sidechainTransactions, blockContext, blockSize).get

    (receiptList, appliedTransactions, AccountBlockFeeInfo(cumBaseFee, cumForgerTips, forgerAddress))
  }

  private def tryApplyAndGetBlockInfo(
      stateView: AccountStateView,
      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
      sidechainTransactions: Iterable[SidechainTypes#SCAT],
      blockContext: BlockContext,
      blockSizeIn: Long
  ): Try[(Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], BigInteger, BigInteger)] = Try {

    for (mcBlockRefData <- mainchainBlockReferencesData) {
      // Since forger still doesn't know the candidate block id we may pass random one.
      val dummyBlockId: ModifierId = bytesToId(new Array[Byte](32))
      stateView.addTopQualityCertificates(mcBlockRefData, dummyBlockId)
      stateView.applyMainchainBlockReferenceData(mcBlockRefData)
    }

    val receiptList = new ListBuffer[EthereumConsensusDataReceipt]()
    val listOfTxsInBlock = new ListBuffer[SidechainTypes#SCAT]()

    var cumGasUsed: BigInteger = BigInteger.ZERO
    var cumBaseFee: BigInteger = BigInteger.ZERO // cumulative base-fee, burned in eth, goes to forgers pool
    var cumForgerTips: BigInteger = BigInteger.ZERO // cumulative max-priority-fee, is paid to block forger

    val blockGasPool = new GasPool(blockContext.blockGasLimit)
    var blockSize = blockSizeIn

    val iter = sidechainTransactions.iterator
    while (iter.hasNext) {
      if (blockGasPool.getGas.compareTo(GasUtil.TxGas) < 0) {
        log.trace(s"Finishing forging because block cannot contain any additional tx")
        return Success(receiptList, listOfTxsInBlock, cumBaseFee, cumForgerTips)
      }

      val priceAndNonceIter = iter.asInstanceOf[TransactionsByPriceAndNonceIter]
      val tx = priceAndNonceIter.peek
      // 1 byte more to account for companion encoding, which will be used when deserializing the block
      val txSize = tx.size() + 1
      if (blockSize + txSize > getMaxBlockSize()) {
        // block size limit exceeded
        // keep trying to fit transactions into the block: this TX did not fit, but another one might
        log.trace(
          s"Could not apply tx, reason: block size limit exceeded (block size: $blockSize, tx size: $txSize, limit: ${getMaxBlockSize()}"
        )
        // skip all txs from the same account
        priceAndNonceIter.removeAndSkipAccount()
      } else {

        stateView.applyTransaction(tx, listOfTxsInBlock.size, blockGasPool, blockContext) match {
          case Success(consensusDataReceipt) =>
            val ethTx = tx.asInstanceOf[EthereumTransaction]

            receiptList += consensusDataReceipt
            listOfTxsInBlock += tx

            val txGasUsed = consensusDataReceipt.cumulativeGasUsed.subtract(cumGasUsed)
            // update cumulative gas used so far
            cumGasUsed = consensusDataReceipt.cumulativeGasUsed

            val (txBaseFeePerGas, txForgerTipPerGas) = GasUtil.getTxFeesPerGas(ethTx, blockContext.baseFee)
            cumBaseFee = cumBaseFee.add(txBaseFeePerGas.multiply(txGasUsed))
            cumForgerTips = cumForgerTips.add(txForgerTipPerGas.multiply(txGasUsed))
            blockSize = blockSize + txSize
            priceAndNonceIter.next()

          case Failure(err) =>
            log.trace(s"Could not apply tx, reason: ${err.getMessage}")
            err match {
              case _: GasLimitReached =>
                // block gas limit reached
                // keep trying to fit transactions into the block: this TX did not fit, but another one might
                // skip all txs from the same account
                priceAndNonceIter.removeAndSkipAccount()
              case _: FeeCapTooLowException =>
                // stop forging because all the remaining txs cannot be executed for the nonce, if they are from the
                // same account, or, if they are from other accounts, they will have a lower fee cap
                return Success(receiptList, listOfTxsInBlock, cumBaseFee, cumForgerTips)
              case e: NonceTooLowException =>
                // SHOULD NEVER HAPPEN, but in case just skip this tx
                log.error(s"******** Could not apply tx for NonceTooLowException ******* : ${e.getMessage}")
                priceAndNonceIter.next()
              case e =>
                // skip all txs from the same account
                log.warn(s"Could not forge tx, reason: ${e.getMessage}", e)
                priceAndNonceIter.removeAndSkipAccount()
            }
        }
      }
    }
    (receiptList, listOfTxsInBlock, cumBaseFee, cumForgerTips)
  }

  override def createNewBlock(
      nodeView: View,
      branchPointInfo: BranchPointInfo,
      isWithdrawalEpochLastBlock: Boolean,
      parentId: BlockId,
      timestamp: Timestamp,
      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
      sidechainTransactions: Iterable[SidechainTypes#SCAT],
      mainchainHeaders: Seq[MainchainHeader],
      ommers: Seq[Ommer[AccountBlockHeader]],
      ownerPrivateKey: PrivateKey25519,
      forgingStakeInfo: ForgingStakeInfo,
      vrfProof: VrfProof,
      vrfOutput: VrfOutput,
      forgingStakeInfoMerklePath: MerklePath,
      companion: DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]],
      inputBlockSize: Int,
      signatureOption: Option[Signature25519],
      isPending: Boolean = false
  ): Try[SidechainBlockBase[SidechainTypes#SCAT, AccountBlockHeader]] = {

    // 1. As forger address take first address from the wallet
    val addressList = nodeView.vault.secretsOfType(classOf[PrivateKeySecp256k1])
    val forgerAddress = addressList.asScala.headOption.map(_.publicImage().asInstanceOf[AddressProposition]).getOrElse(
      if (isPending) new AddressProposition(Address.ZERO)
      else throw new IllegalArgumentException("No addresses in wallet!")
    )

    // 2. calculate baseFee
    val baseFee = calculateBaseFee(nodeView.history, parentId)

    // 3. Set gasLimit
    val feeFork = GasFeeFork.get(TimeToEpochUtils.timeStampToEpochNumber(params, timestamp))
    val gasLimit: BigInteger = feeFork.blockGasLimit

    // 4. create a context for the new block
    // this will throw if parent block was not found
    val parentInfo = nodeView.history.blockInfoById(parentId)
    val blockContext = new BlockContext(
      forgerAddress.address(),
      timestamp,
      baseFee,
      gasLimit,
      parentInfo.height + 1,
      TimeToEpochUtils.timeStampToEpochNumber(params, timestamp),
      WithdrawalEpochUtils
        .getWithdrawalEpochInfo(mainchainBlockReferencesData.size, parentInfo.withdrawalEpochInfo, params)
        .epoch,
      params.chainId,
      nodeView.history,
      new Hash(vrfOutput.bytes())
    )

    // 5. create a disposable view and try to apply all transactions in the list and apply fee payments if needed, collecting all data needed for
    //    going on with the forging of the block
    val (stateRoot, receiptList, appliedTxList, feePayments)
        : (Array[Byte], Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], Seq[AccountPayment]) =
      if (nodeView.history.bestBlockId == branchPointInfo.branchPointId) {
        using(nodeView.state.getView) {
          dummyView =>
            // the outputs of the next call will be:
            // - the list of receipt of the transactions successfully applied ---> for getting the receiptsRoot
            // - the list of transactions successfully applied to the state ---> to be included in the forged block
            // - the fee payments related to this block
            val resultTuple: (Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], AccountBlockFeeInfo) =
              computeBlockInfo(
                dummyView,
                sidechainTransactions,
                mainchainBlockReferencesData,
                blockContext,
                forgerAddress,
                inputBlockSize
              )

            val receiptList = resultTuple._1
            val appliedTxList = resultTuple._2
            val currentBlockPayments = resultTuple._3

            val feePayments = if (isWithdrawalEpochLastBlock) {
              // Current block is expected to be the continuation of the current tip, so there are no ommers.
              require(
                nodeView.history.bestBlockId == branchPointInfo.branchPointId,
                "Last block of the withdrawal epoch expect to be a continuation of the tip."
              )
              require(ommers.isEmpty, "No Ommers allowed for the last block of the withdrawal epoch.")

              val withdrawalEpochNumber: Int = dummyView.getWithdrawalEpochInfo.epoch

              // get all previous payments for current ending epoch and append the one of the current block
              val feePayments = dummyView.getFeePaymentsInfo(withdrawalEpochNumber, Some(currentBlockPayments))

              // add rewards to forgers balance
              feePayments.foreach(payment => dummyView.addBalance(payment.address.address(), payment.value))

              feePayments
            } else {
              Seq()
            }

            (dummyView.getIntermediateRoot, receiptList, appliedTxList, feePayments)
        }
      } else {
        // This happens when there is a fork in Mainchain. The SC blocks referencing the old MC chain will be reverted
        // and the new SC blocks will be created on top the SC block referencing the MC branching point.
        // The first new SC block must not contain FT or transactions.
        require(ommers.nonEmpty, "Expected ommers when branching point is not the blockchain tip")
        require(sidechainTransactions.isEmpty, "No txs expected in a block with ommers")
        require(mainchainBlockReferencesData.isEmpty, "No Mainchain reference data expected in a block with ommers")
        (
          nodeView.history.getBlockById(parentId).get().header.stateRoot,
          Seq.empty[EthereumConsensusDataReceipt],
          Seq.empty[SidechainTypes#SCAT],
          Seq.empty[AccountPayment]
        )
      }

    // 6. Compute the receipt root
    val receiptsRoot: Array[Byte] = calculateReceiptRoot(receiptList)

    // 7. Get cumulativeGasUsed from last receipt in list if available
    val gasUsed: BigInteger = receiptList.lastOption.map(_.cumulativeGasUsed).getOrElse(BigInteger.ZERO)

    // 8. set the fee payments hash
    val feePaymentsHash: Array[Byte] = AccountFeePaymentsUtils.calculateFeePaymentsHash(feePayments)

    val logsBloom = Bloom.fromReceipts(receiptList)

    val block = AccountBlock.create(
      parentId,
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      timestamp,
      mainchainBlockReferencesData,
      appliedTxList,
      mainchainHeaders,
      ommers,
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      vrfOutput,
      forgingStakeInfoMerklePath,
      feePaymentsHash,
      stateRoot,
      receiptsRoot,
      forgerAddress,
      baseFee,
      gasUsed,
      gasLimit,
      companion.asInstanceOf[SidechainAccountTransactionsCompanion],
      logsBloom
    )
    block
  }

  override def precalculateBlockHeaderSize(
      parentId: ModifierId,
      timestamp: Long,
      forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo,
      vrfProof: VrfProof
  ): Int = {
    // Create block header template, setting dummy values where it is possible.
    // Note:  AccountBlockHeader length is not constant because of the forgingStakeMerklePathInfo.merklePath.
    val header = AccountBlockHeader(
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      parentId,
      timestamp,
      forgingStakeMerklePathInfo.forgingStakeInfo,
      forgingStakeMerklePathInfo.merklePath,
      vrfProof,
      new VrfOutput(new Array[Byte](VrfOutput.OUTPUT_LENGTH)),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      AddressProposition.ZERO,
      BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE),
      BigInteger.valueOf(Long.MaxValue),
      BigInteger.valueOf(Long.MaxValue),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      Long.MaxValue,
      new Array[Byte](NodeViewModifier.ModifierIdSize),
      new Bloom(),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    header.bytes.length
  }

  override def collectTransactionsFromMemPool(
      nodeView: View,
      blockSizeIn: Int,
      mainchainBlockReferenceData: Seq[MainchainBlockReferenceData],
      withdrawalEpochInfo: WithdrawalEpochInfo,
      timestamp: Timestamp,
      forcedTx: Iterable[SidechainTypes#SCAT]
  ): MempoolMap#TransactionsByPriceAndNonce = {
    // no checks of the block size here, these txes are the candidates and their inclusion
    // will be attempted by forger

    nodeView.pool.takeExecutableTxs(forcedTx)
  }

  override def getOmmersSize(ommers: Seq[Ommer[AccountBlockHeader]]): Int = {
    val ommersSerializer = new ListSerializer[Ommer[AccountBlockHeader]](AccountOmmerSerializer)
    ommersSerializer.toBytes(ommers.asJava).length
  }

  def getStateRoot(
      history: AccountHistory,
      forgedBlockTimestamp: Long,
      branchPointInfo: BranchPointInfo
  ): Array[Byte] = {
    // For given epoch N we should get data from the ending of the epoch N-2.
    // genesis block is the single and the last block of epoch 1 - that is a special case:
    // Data from epoch 1 is also valid for epoch 2, so for epoch N==2, we should get info from epoch 1.
    val parentId = branchPointInfo.branchPointId
    val lastBlockId = history.getLastBlockIdOfPrePreviousEpochs(forgedBlockTimestamp, parentId)
    val lastBlock = history.getBlockById(lastBlockId)
    lastBlock.get().header.stateRoot
  }

  override def getForgingStakeMerklePathInfo(
      nextConsensusEpochNumber: ConsensusEpochNumber,
      wallet: AccountWallet,
      history: AccountHistory,
      state: AccountState,
      branchPointInfo: BranchPointInfo,
      nextBlockTimestamp: Long
  ): Seq[ForgingStakeMerklePathInfo] = {

    // 1. get from history the state root from the header of the block of 2 epochs before
    val stateRoot = getStateRoot(history, nextBlockTimestamp, branchPointInfo)

    // 2. get from stateDb using root above the collection of all forger stakes (ordered)
    val forgingStakeInfoSeq: Seq[ForgingStakeInfo] = using(state.getStateDbViewFromRoot(stateRoot)) {
      stateViewFromRoot =>
        stateViewFromRoot.getOrderedForgingStakesInfoSeq
    }

    // 3. using wallet secrets, filter out the not-mine forging stakes
    val secrets: Seq[Secret] = wallet.allSecrets().asScala

    val walletPubKeys = secrets.map(e => e.publicImage())

    val filteredForgingStakeInfoSeq = forgingStakeInfoSeq.filter(p => {
      walletPubKeys.contains(p.blockSignPublicKey) &&
      walletPubKeys.contains(p.vrfPublicKey)
    })

    // return an empty seq if we do not have forging stake, that is a legal (negative) result.
    if (filteredForgingStakeInfoSeq.isEmpty)
      return Seq()

    // 4. prepare merkle tree of all forger stakes and extract path info of mine (what is left after 3)
    val forgingStakeInfoTree = MerkleTree.createMerkleTree(forgingStakeInfoSeq.map(info => info.hash).asJava)
    val merkleTreeLeaves = forgingStakeInfoTree.leaves().asScala.map(leaf => new ByteArrayWrapper(leaf))

    // Calculate merkle path for all delegated forger stakes
    val forgingStakeMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo] =
      filteredForgingStakeInfoSeq.flatMap(forgingStakeInfo => {
        merkleTreeLeaves.indexOf(new ByteArrayWrapper(forgingStakeInfo.hash)) match {
          case -1 =>
            log.warn(s"ForgingStakeInfo not a leaf in merkle tree: should never happen: $forgingStakeInfo ")
            None
          case index =>
            Some(ForgingStakeMerklePathInfo(forgingStakeInfo, forgingStakeInfoTree.getMerklePathForLeaf(index)))
        }
      })

    forgingStakeMerklePathInfoSeq
  }

  def getPendingBlock(nodeView: View): Option[AccountBlock] = {
    val bestBlockId: ModifierId = nodeView.history.bestBlockId
    val branchPointInfo = BranchPointInfo(bestBlockId, Seq(), Seq())
    val blockSignPrivateKey = new PrivateKey25519(
      new Array[Byte](PrivateKey25519.PRIVATE_KEY_LENGTH),
      new Array[Byte](PrivateKey25519.PUBLIC_KEY_LENGTH)
    )
    val forgingStakeInfo: ForgingStakeInfo = new ForgingStakeInfo(
      new PublicKey25519Proposition(new Array[Byte](PublicKey25519Proposition.KEY_LENGTH)),
      new VrfPublicKey(new Array[Byte](VrfPublicKey.KEY_LENGTH)),
      0
    )
    val forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo =
      ForgingStakeMerklePathInfo(forgingStakeInfo, new MerklePath(new JArrayList()))
    val vrfProof: VrfProof = new VrfProof(new Array[Byte](VrfProof.PROOF_LENGTH))

    // keep pending block VRFOutput same as for current tip one
    // it is used as a source of BlockContext.random
    val bestBlockInfo = nodeView.history.blockInfoById(bestBlockId)
    val vrfOutput: VrfOutput = bestBlockInfo.vrfOutputOpt.getOrElse(
      new VrfOutput(new Array[Byte](VrfOutput.OUTPUT_LENGTH))
    )

    // we have not the constraint of consensusSlot duration since we are not really forging
    implicit val mcRefDataRetrievalTimeout: Timeout = new Timeout(5, SECONDS)

    forgeBlock(
      nodeView,
      System.currentTimeMillis / 1000,
      branchPointInfo,
      forgingStakeMerklePathInfo,
      blockSignPrivateKey,
      vrfProof,
      vrfOutput,
      mcRefDataRetrievalTimeout,
      Seq(),
      isPending = true
    ) match {
      case ForgeSuccess(block) => Option.apply(block.asInstanceOf[AccountBlock])
      case _: ForgeFailure => Option.empty
    }
  }

  override def getMaxBlockOverheadSize(): Int = AccountBlock.MAX_ACCOUNT_BLOCK_OVERHEAD_SIZE
  override def getMaxBlockSize(): Int = AccountBlock.MAX_ACCOUNT_BLOCK_SIZE

}
