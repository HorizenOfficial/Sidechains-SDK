package com.horizen.account.forger

import com.horizen.SidechainTypes
import com.horizen.account.utils.{Account, AccountBlockFeeInfo, AccountFeePaymentsUtils, AccountPayment, FeeUtils}
import com.horizen.account.utils.FeeUtils.calculateBaseFee
import com.horizen.account.block.AccountBlock.calculateReceiptRoot
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.wallet.AccountWallet
import com.horizen.block._
import com.horizen.chain.MainchainHeaderHash
import com.horizen.consensus._
import com.horizen.forge.{AbstractForgeMessageBuilder, MainchainSynchronizer}
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.{PrivateKey25519, Secret}
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ClosableResourceHandler, DynamicTypedSerializer, ForgingStakeMerklePathInfo, ListSerializer, MerklePath, MerkleTree, TimeToEpochUtils, WithdrawalEpochUtils}
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.block.Block.{BlockId, Timestamp}
import sparkz.core.{NodeViewModifier, idToBytes}
import java.math.BigInteger
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
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
      with ClosableResourceHandler
      with ScorexLogging {
  type FPI = AccountFeePaymentsInfo
  type HSTOR = AccountHistoryStorage
  type VL = AccountWallet
  type HIS = AccountHistory
  type MS = AccountState
  type MP = AccountMemoryPool


  def computeBlockInfo(
                        view: AccountStateView,
                        sidechainTransactions: Seq[SidechainTypes#SCAT],
                        mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                        blockContext: BlockContext,
                        forgerAddress: AddressProposition)
  : (Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], AccountBlockFeeInfo) = {

    // we must ensure that all the tx we get from mempool are applicable to current state view
    // and we must stay below the block gas limit threshold, therefore we might have a subset of the input transactions

    val (receiptList, listOfAppliedTxHash, cumBaseFee, cumForgerTips) =
      tryApplyAndGetBlockInfo(view, mainchainBlockReferencesData, sidechainTransactions, blockContext).get

    // get only the transactions for which we got a receipt
    val appliedTransactions = sidechainTransactions.filter {
      t => {
        val txHash = idToBytes(t.id)
        listOfAppliedTxHash.contains(new ByteArrayWrapper(txHash))
      }
    }

    (receiptList, appliedTransactions, AccountBlockFeeInfo(cumBaseFee, cumForgerTips, forgerAddress))
  }

  private def tryApplyAndGetBlockInfo(stateView: AccountStateView,
                                      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
                                      sidechainTransactions: Seq[SidechainTypes#SCAT],
                                      blockContext: BlockContext)
  : Try[(Seq[EthereumConsensusDataReceipt], Seq[ByteArrayWrapper], BigInteger, BigInteger)] = Try {

    for (mcBlockRefData <- mainchainBlockReferencesData) {
      stateView.applyMainchainBlockReferenceData(mcBlockRefData).get
    }

    val receiptList = new ListBuffer[EthereumConsensusDataReceipt]()
    val txHashList = new ListBuffer[ByteArrayWrapper]()

    val blockGasPool = new GasPool(BigInteger.valueOf(blockContext.blockGasLimit))

    var cumGasUsed: BigInteger = BigInteger.ZERO
    var cumBaseFee: BigInteger = BigInteger.ZERO // cumulative base-fee, burned in eth, goes to forgers pool
    var cumForgerTips: BigInteger = BigInteger.ZERO  // cumulative max-priority-fee, is paid to block forger
    val accountsToSkip = new mutable.HashSet[SidechainTypes#SCP]

    for ((tx, txIndex) <- sidechainTransactions.zipWithIndex if !accountsToSkip.contains(tx.getFrom)) {

      stateView.applyTransaction(tx, txIndex, blockGasPool, blockContext) match {
        case Success(consensusDataReceipt) =>

          val ethTx = tx.asInstanceOf[EthereumTransaction]
          val txHash = BytesUtils.fromHexString(ethTx.id)

          receiptList += consensusDataReceipt
          txHashList += txHash

          val txGasUsed = consensusDataReceipt.cumulativeGasUsed.subtract(cumGasUsed)
          // update cumulative gas used so far
          cumGasUsed = consensusDataReceipt.cumulativeGasUsed

          val baseFeePerGas = blockContext.baseFee
          val (txBaseFeePerGas, txForgerTipPerGas) = GasUtil.getTxFeesPerGas(ethTx, baseFeePerGas)
          cumBaseFee = cumBaseFee.add(txBaseFeePerGas.multiply(txGasUsed))
          cumForgerTips = cumForgerTips.add(txForgerTipPerGas.multiply(txGasUsed))

       case Failure(e: GasLimitReached) =>
          // block gas limit reached
          // keep trying to fit transactions into the block: this TX did not fit, but another one might
          log.trace(s"Could not apply tx, reason: ${e.getMessage}")
          // skip all txs from the same account
          accountsToSkip += tx.getFrom
        case Failure(e: FeeCapTooLowException) =>
          // stop forging because all the remaining txs cannot be executed for the nonce, if they are from the same account, or,
          // if they are from other accounts, they will have a lower fee cap
          log.trace(s"Could not apply tx, reason: ${e.getMessage}")
          return Success(receiptList, txHashList, cumBaseFee, cumForgerTips)
        case Failure(e: NonceTooLowException) =>
          //SHOULD NEVER HAPPEN, but in case just skip this tx
         log.error(s"******** Could not apply tx for NonceTooLowException ******* : ${e.getMessage}")
        case Failure(e) =>
          // skip all txs from the same account
          accountsToSkip += tx.getFrom
          log.trace(s"Could not apply tx, reason: ${e.getMessage}. Skipping all next transactions from the same account because not executable anymore",e)
      }
    }
    (receiptList, txHashList, cumBaseFee, cumForgerTips)
  }

  override def createNewBlock(
      nodeView: View,
      branchPointInfo: BranchPointInfo,
      isWithdrawalEpochLastBlock: Boolean,
      parentId: BlockId,
      timestamp: Timestamp,
      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
      sidechainTransactions: Seq[SidechainTypes#SCAT],
      mainchainHeaders: Seq[MainchainHeader],
      ommers: Seq[Ommer[AccountBlockHeader]],
      ownerPrivateKey: PrivateKey25519,
      forgingStakeInfo: ForgingStakeInfo,
      vrfProof: VrfProof,
      forgingStakeInfoMerklePath: MerklePath,
      companion: DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]],
      inputBlockSize: Int,
      signatureOption: Option[Signature25519]
  ): Try[SidechainBlockBase[SidechainTypes#SCAT, AccountBlockHeader]] = {

    // 1. As forger address take first address from the wallet
    val addressList = nodeView.vault.secretsOfType(classOf[PrivateKeySecp256k1])
    if (addressList.size() == 0)
      throw new IllegalArgumentException("No addresses in wallet!")
    val forgerAddress = addressList.get(0).publicImage().asInstanceOf[AddressProposition]

    // 2. calculate baseFee
    val baseFee = calculateBaseFee(nodeView.history, parentId)

    // 3. Set gasLimit
    val gasLimit = FeeUtils.GAS_LIMIT

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
        .getWithdrawalEpochInfo(mainchainBlockReferencesData, parentInfo.withdrawalEpochInfo, params)
        .epoch
    )

    // 5. create a disposable view and try to apply all transactions in the list and apply fee payments if needed, collecting all data needed for
    //    going on with the forging of the block
    val (stateRoot, receiptList, appliedTxList, feePayments)
    : (Array[Byte], Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], Seq[AccountPayment]) = {
        using(nodeView.state.getView) {
          dummyView =>
            // the outputs of the next call will be:
            // - the list of receipt of the transactions successfully applied ---> for getting the receiptsRoot
            // - the list of transactions successfully applied to the state ---> to be included in the forged block
            // - the fee payments related to this block
            val resultTuple : (Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], AccountBlockFeeInfo) =
              computeBlockInfo(dummyView, sidechainTransactions, mainchainBlockReferencesData, blockContext, forgerAddress)

            val receiptList = resultTuple._1
            val appliedTxList = resultTuple._2
            val currentBlockPayments = resultTuple._3

            val feePayments = if(isWithdrawalEpochLastBlock) {
              // Current block is expected to be the continuation of the current tip, so there are no ommers.
              require(nodeView.history.bestBlockId == branchPointInfo.branchPointId, "Last block of the withdrawal epoch expect to be a continuation of the tip.")
              require(ommers.isEmpty, "No Ommers allowed for the last block of the withdrawal epoch.")

              val withdrawalEpochNumber: Int = dummyView.getWithdrawalEpochInfo.epoch

              // get all previous payments for current ending epoch and append the one of the current block
              val feePayments = dummyView.getFeePayments(withdrawalEpochNumber, Some(currentBlockPayments))

              // add rewards to forgers balance
              feePayments.foreach(payment => dummyView.addBalance(payment.addressBytes, payment.value))

              feePayments
            } else {
              Seq()
            }

            (dummyView.getIntermediateRoot, receiptList, appliedTxList, feePayments)
        }
    }

    // 6. Compute the receipt root
    val receiptsRoot: Array[Byte] = calculateReceiptRoot(receiptList)

    // 7. Get cumulativeGasUsed from last receipt in list if available
    val gasUsed: Long = receiptList.lastOption.map(_.cumulativeGasUsed.longValue()).getOrElse(0)

    // 8. set the fee payments hash
    val feePaymentsHash: Array[Byte] = AccountFeePaymentsUtils.calculateFeePaymentsHash(feePayments)

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
      forgingStakeInfoMerklePath,
      feePaymentsHash,
      stateRoot,
      receiptsRoot,
      forgerAddress,
      baseFee,
      gasUsed,
      gasLimit,
      companion.asInstanceOf[SidechainAccountTransactionsCompanion]
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
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      // stateRoot TODO add constant
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      // forgerAddress: PublicKeySecp256k1Proposition TODO add constant
      new AddressProposition(new Array[Byte](Account.ADDRESS_SIZE)),
      BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE),
      Long.MaxValue,
      Long.MaxValue,
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      Long.MaxValue,
      new Array[Byte](NodeViewModifier.ModifierIdSize),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    header.bytes.length
  }

  override def collectTransactionsFromMemPool(nodeView: View, blockSizeIn: Int, mainchainBlockReferenceDataToRetrieve: Seq[MainchainHeaderHash], timestamp: Long, forcedTx: Iterable[SidechainTypes#SCAT]) : Seq[SidechainTypes#SCAT] = {
    // no checks of the block size here, these txes are the candidates and their inclusion
    // will be attempted by forger

    nodeView.pool.take(nodeView.pool.size).toSeq
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
        stateViewFromRoot.getOrderedForgingStakeInfoSeq
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

    // Calculate merkle path for all delegated forgerBoxes
    val forgingStakeMerklePathInfoSeq: Seq[ForgingStakeMerklePathInfo] =
      filteredForgingStakeInfoSeq.flatMap(forgingStakeInfo => {
        merkleTreeLeaves.indexOf(new ByteArrayWrapper(forgingStakeInfo.hash)) match {
          case -1 => None // May occur in case Wallet doesn't contain information about blockSignKey and vrfKey
          case index =>
            Some(ForgingStakeMerklePathInfo(forgingStakeInfo, forgingStakeInfoTree.getMerklePathForLeaf(index)))
        }
      })

    forgingStakeMerklePathInfoSeq
  }
}
