package com.horizen.account.api.rpc.service.utils

import akka.util.Timeout
import com.horizen.SidechainTypes
import com.horizen.account.block.AccountBlock.calculateReceiptRoot
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.companion.SidechainAccountTransactionsCompanion
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.{AccountMemoryPool, MempoolMap, TransactionsByPriceAndNonceIter}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.{Bloom, EthereumConsensusDataReceipt}
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.FeeUtils.calculateBaseFee
import com.horizen.account.utils._
import com.horizen.account.wallet.AccountWallet
import com.horizen.block._
import com.horizen.chain.{MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.params.NetworkParams
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.{
  ClosableResourceHandler,
  DynamicTypedSerializer,
  ListSerializer,
  MerklePath,
  MerkleTree,
  TimeToEpochUtils,
  WithdrawalEpochUtils
}
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.block.Block.{BlockId, Timestamp}
import sparkz.core.consensus.ModifierSemanticValidity
import sparkz.core.{NodeViewModifier, bytesToId}

import java.math.BigInteger
import java.util.{ArrayList => JArrayList}
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.concurrent.duration.SECONDS
import scala.util.{Failure, Success, Try}

case class BranchPointInfo(
    branchPointId: ModifierId,
    referenceDataToInclude: Seq[MainchainHeaderHash],
    headersToInclude: Seq[MainchainHeaderHash]
)

class PendingBlock(nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool])
    extends ScorexLogging with ClosableResourceHandler {
  type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]
  private val history: AccountHistory = nodeView.history
  private val state: AccountState = nodeView.state
  private val networkParams: NetworkParams = state.params
  private var sidechainBlockInfo: SidechainBlockInfo = null

  private def getBranchPointInfo(history: AccountHistory): Try[BranchPointInfo] = Try {
    val bestMainchainHeaderInfo = history.getBestMainchainHeaderInfo.get

    val (
      bestMainchainCommonPointHeight: Int,
      bestMainchainCommonPointHash: MainchainHeaderHash,
      newHeaderHashes: Seq[MainchainHeaderHash]
    ) = (bestMainchainHeaderInfo.height, bestMainchainHeaderInfo.hash, Seq())

    // Check that there is no orphaned mainchain headers: SC most recent mainchain header is a part of MC active chain
    if (bestMainchainCommonPointHash == bestMainchainHeaderInfo.hash) {
      val branchPointId: ModifierId = history.bestBlockId
      var withdrawalEpochMcBlocksLeft =
        networkParams.withdrawalEpochLength - history.bestBlockInfo.withdrawalEpochInfo.lastEpochIndex
      if (withdrawalEpochMcBlocksLeft == 0) // current best block is the last block of the epoch
        withdrawalEpochMcBlocksLeft = networkParams.withdrawalEpochLength

      // to not to include mcblock references data from different withdrawal epochs
      val maxReferenceDataNumber: Int = withdrawalEpochMcBlocksLeft

      val missedMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] =
        history.missedMainchainReferenceDataHeaderHashes
      val nextMainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] =
        missedMainchainReferenceDataHeaderHashes ++ newHeaderHashes

      val mainchainReferenceDataHeaderHashesToInclude =
        nextMainchainReferenceDataHeaderHashes.take(maxReferenceDataNumber)
      val mainchainHeadersHashesToInclude = newHeaderHashes

      BranchPointInfo(branchPointId, mainchainReferenceDataHeaderHashesToInclude, mainchainHeadersHashesToInclude)
    } else { // Some blocks in SC Active chain contains orphaned MainchainHeaders
      val orphanedMainchainHeadersNumber: Int = bestMainchainHeaderInfo.height - bestMainchainCommonPointHeight
      val newMainchainHeadersNumber = newHeaderHashes.size

      if (orphanedMainchainHeadersNumber >= newMainchainHeadersNumber) {
        throw new Exception(
          "No sense to forge: active branch contains orphaned MainchainHeaders, that number is greater or equal to actual new MainchainHeaders."
        )
      }

      val firstOrphanedHashHeight: Int = bestMainchainCommonPointHeight + 1
      val firstOrphanedMainchainHeaderInfo = history.getMainchainHeaderInfoByHeight(firstOrphanedHashHeight).get
      val orphanedSidechainBlockId: ModifierId = firstOrphanedMainchainHeaderInfo.sidechainBlockId
      val orphanedSidechainBlockInfo: SidechainBlockInfo = history.blockInfoById(orphanedSidechainBlockId)

      if (firstOrphanedMainchainHeaderInfo.hash.equals(orphanedSidechainBlockInfo.mainchainHeaderHashes.head)) {
        // First orphaned MainchainHeader is the first header inside the containing SidechainBlock, so no common MainchainHeaders present in SidechainBlock before it
        BranchPointInfo(orphanedSidechainBlockInfo.parentId, Seq(), newHeaderHashes)
      } else {
        // SidechainBlock also contains some common MainchainHeaders before first orphaned MainchainHeader
        // So we should add that common MainchainHeaders to the SidechainBlock as well
        BranchPointInfo(
          orphanedSidechainBlockInfo.parentId,
          Seq(),
          orphanedSidechainBlockInfo.mainchainHeaderHashes.takeWhile(hash =>
            !hash.equals(firstOrphanedMainchainHeaderInfo.hash)
          ) ++ newHeaderHashes
        )
      }
    }
  }

  private def tryApplyAndGetBlockInfo(
      stateView: AccountStateView,
      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
      sidechainTransactions: Iterable[SidechainTypes#SCAT],
      blockContext: BlockContext
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

    val iter = sidechainTransactions.iterator
    while (iter.hasNext) {
      if (blockGasPool.getGas.compareTo(GasUtil.TxGas) < 0) {
        log.trace(s"Finishing forging because block cannot contain any additional tx")
        return Success(receiptList, listOfTxsInBlock, cumBaseFee, cumForgerTips)
      }
      val revisionId = stateView.snapshot
      val initialBlockGas = blockGasPool.getGas
      val priceAndNonceIter = iter.asInstanceOf[TransactionsByPriceAndNonceIter]

      try {
        val tx = priceAndNonceIter.peek
        stateView.applyTransaction(
          tx,
          listOfTxsInBlock.size,
          blockGasPool,
          blockContext,
          finalizeChanges = false
        ) match {
          case Success(consensusDataReceipt) =>
            val ethTx = tx.asInstanceOf[EthereumTransaction]

            receiptList += consensusDataReceipt
            listOfTxsInBlock += tx

            val txGasUsed = consensusDataReceipt.cumulativeGasUsed.subtract(cumGasUsed)
            // update cumulative gas used so far
            cumGasUsed = consensusDataReceipt.cumulativeGasUsed

            val baseFeePerGas = blockContext.baseFee
            val (txBaseFeePerGas, txForgerTipPerGas) = GasUtil.getTxFeesPerGas(ethTx, baseFeePerGas)
            cumBaseFee = cumBaseFee.add(txBaseFeePerGas.multiply(txGasUsed))
            cumForgerTips = cumForgerTips.add(txForgerTipPerGas.multiply(txGasUsed))
            priceAndNonceIter.next()
          case Failure(e: GasLimitReached) =>
            // block gas limit reached
            // keep trying to fit transactions into the block: this TX did not fit, but another one might
            log.trace(s"Could not apply tx, reason: ${e.getMessage}")
            // skip all txs from the same account
            priceAndNonceIter.removeAndSkipAccount()
          case Failure(e: FeeCapTooLowException) =>
            // stop forging because all the remaining txs cannot be executed for the nonce, if they are from the same account, or,
            // if they are from other accounts, they will have a lower fee cap
            log.trace(s"Could not apply tx, reason: ${e.getMessage}")
            return Success(receiptList, listOfTxsInBlock, cumBaseFee, cumForgerTips)
          case Failure(e: NonceTooLowException) =>
            // SHOULD NEVER HAPPEN, but in case just skip this tx
            log.error(s"******** Could not apply tx for NonceTooLowException ******* : ${e.getMessage}")
            priceAndNonceIter.next()
          case Failure(e) =>
            // skip all txs from the same account but remove any changes caused by the rejected tx
            log.warn(s"Could not forge tx, reason: ${e.getMessage}", e)
            priceAndNonceIter.removeAndSkipAccount()
            stateView.revertToSnapshot(revisionId)
            // Restore gas
            val usedGas = initialBlockGas.subtract(blockGasPool.getGas)
            blockGasPool.addGas(usedGas)
        }
      } finally {
        stateView.finalizeChanges()
      }

    }
    (receiptList, listOfTxsInBlock, cumBaseFee, cumForgerTips)
  }

  private def computeBlockInfo(
      view: AccountStateView,
      sidechainTransactions: Iterable[SidechainTypes#SCAT],
      mainchainBlockReferencesData: Seq[MainchainBlockReferenceData],
      blockContext: BlockContext,
      forgerAddress: AddressProposition
  ): (Seq[EthereumConsensusDataReceipt], Seq[SidechainTypes#SCAT], AccountBlockFeeInfo) = {

    // we must ensure that all the tx we get from mempool are applicable to current state view
    // and we must stay below the block gas limit threshold, therefore we might have a subset of the input transactions

    val (receiptList, appliedTransactions, cumBaseFee, cumForgerTips) =
      tryApplyAndGetBlockInfo(view, mainchainBlockReferencesData, sidechainTransactions, blockContext).get

    (receiptList, appliedTransactions, AccountBlockFeeInfo(cumBaseFee, cumForgerTips, forgerAddress))
  }

  def createNewBlock(
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
      forgingStakeInfoMerklePath: MerklePath,
      companion: DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]]
  ): (Try[SidechainBlockBase[SidechainTypes#SCAT, AccountBlockHeader]]) = {

    // 1. As forger address take first address from the wallet
    val addressList = nodeView.vault.secretsOfType(classOf[PrivateKeySecp256k1])
    if (addressList.size() == 0)
      throw new IllegalArgumentException("No addresses in wallet!")
    val forgerAddress = addressList.get(0).publicImage().asInstanceOf[AddressProposition]

    // 2. calculate baseFee
    val baseFee = calculateBaseFee(history, parentId)

    // 3. Set gasLimit
    val gasLimit: BigInteger = FeeUtils.GAS_LIMIT

    // 4. create a context for the new block
    // this will throw if parent block was not found
    val parentInfo = history.blockInfoById(parentId)
    val blockContext = new BlockContext(
      forgerAddress.address(),
      timestamp,
      baseFee,
      gasLimit,
      parentInfo.height + 1,
      TimeToEpochUtils.timeStampToEpochNumber(networkParams, timestamp),
      WithdrawalEpochUtils
        .getWithdrawalEpochInfo(mainchainBlockReferencesData.size, parentInfo.withdrawalEpochInfo, networkParams)
        .epoch,
      networkParams.chainId
    )

    // 5. create a disposable view and try to apply all transactions in the list and apply fee payments if needed, collecting all data needed for
    //    going on with the forging of the block
    val (stateRoot, receiptList, appliedTxList, feePayments): (
        Array[Byte],
        Seq[EthereumConsensusDataReceipt],
        Seq[SidechainTypes#SCAT],
        Seq[AccountPayment]
    ) = {
      using(state.getView) {
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
              forgerAddress
            )

          val receiptList = resultTuple._1
          val appliedTxList = resultTuple._2
          val currentBlockPayments = resultTuple._3
          val feePayments = if (isWithdrawalEpochLastBlock) {
            // Current block is expected to be the continuation of the current tip, so there are no ommers.
            require(
              history.bestBlockId == branchPointInfo.branchPointId,
              "Last block of the withdrawal epoch expect to be a continuation of the tip."
            )
            require(ommers.isEmpty, "No Ommers allowed for the last block of the withdrawal epoch.")

            val withdrawalEpochNumber: Int = dummyView.getWithdrawalEpochInfo.epoch

            // get all previous payments for current ending epoch and append the one of the current block
            val feePayments = dummyView.getFeePaymentsInfo(withdrawalEpochNumber, Some(currentBlockPayments))

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

    sidechainBlockInfo = new SidechainBlockInfo(
      parentInfo.height + 1,
      parentInfo.score + 1,
      parentId,
      timestamp,
      ModifierSemanticValidity.Unknown,
      null,
      null,
      WithdrawalEpochUtils.getWithdrawalEpochInfo(
        mainchainBlockReferencesData.size,
        parentInfo.withdrawalEpochInfo,
        networkParams
      ),
      None,
      parentInfo.lastBlockInPreviousConsensusEpoch
    )

    block
  }

  def getBlockInfo: SidechainBlockInfo = {
    sidechainBlockInfo
  }

  def precalculateBlockHeaderSize(
      parentId: ModifierId,
      timestamp: Long
  ): Int = {

    // Create block header template, setting dummy values where it is possible.
    // Note:  AccountBlockHeader length is not constant because of the forgingStakeMerklePathInfo.merklePath.
    val header = AccountBlockHeader(
      AccountBlock.ACCOUNT_BLOCK_VERSION,
      parentId,
      timestamp,
      new ForgingStakeInfo(
        new PublicKey25519Proposition(new Array[Byte](PublicKey25519Proposition.KEY_LENGTH)),
        new VrfPublicKey(new Array[Byte](VrfPublicKey.KEY_LENGTH)),
        0
      ),
      new MerklePath(new JArrayList()),
      new VrfProof(new Array[Byte](VrfProof.PROOF_LENGTH)),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new Array[Byte](MerkleTree.ROOT_HASH_LENGTH),
      new AddressProposition(new Array[Byte](Account.ADDRESS_SIZE)),
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

  def collectTransactionsFromMemPool(
      blockSizeIn: Int
  ): MempoolMap#TransactionsByPriceAndNonce = {
    // no checks of the block size here, these txes are the candidates and their inclusion
    // will be attempted by forger

    nodeView.pool.takeExecutableTxs()
  }

  def getOmmersSize(ommers: Seq[Ommer[AccountBlockHeader]]): Int = {
    val ommersSerializer = new ListSerializer[Ommer[AccountBlockHeader]](AccountOmmerSerializer)
    ommersSerializer.toBytes(ommers.asJava).length
  }

  def getPendingBlock: AccountBlock = {
    val branchPointInfo = getBranchPointInfo(history).get
    val parentBlockId: ModifierId = branchPointInfo.branchPointId
    implicit val timeout: Timeout = new Timeout(5, SECONDS)
    var blockSize: Int = precalculateBlockHeaderSize(parentBlockId, System.currentTimeMillis / 1000)
    blockSize += 4 + 4 // placeholder for the MainchainReferenceData and Transactions sequences size values

    // Get all needed MainchainBlockHeaders from MC Node
    val mainchainHeaderHashesToRetrieve: Seq[MainchainHeaderHash] = branchPointInfo.headersToInclude

    // Extract proper MainchainHeaders
    val mainchainHeaders: Seq[MainchainHeader] =
      mainchainHeaderHashesToRetrieve.flatMap(hash => Seq(history.getMainchainHeaderByHash(hash.data()).get))

    // Update block size with MC Headers
    val mcHeadersSerializer = new ListSerializer[MainchainHeader](MainchainHeaderSerializer)
    blockSize += mcHeadersSerializer.toBytes(mainchainHeaders.asJava).length

    // Get Ommers in case the branch point is not the current best block
    var ommers: Seq[Ommer[AccountBlockHeader]] = Seq()
    var blockId = history.bestBlockId
    while (blockId != parentBlockId) {
      val block = history.getBlockById(blockId).get
      blockId = block.parentId
      ommers = Ommer.toOmmer(block) +: ommers
    }

    // Update block size with Ommers
    blockSize += getOmmersSize(ommers)

    // Get all needed MainchainBlockReferences from the MC Node
    // Extract MainchainReferenceData and collect as much as the block can fit
    val mainchainBlockReferenceDataToRetrieve: Seq[MainchainHeaderHash] = branchPointInfo.referenceDataToInclude

    val mainchainReferenceData: ArrayBuffer[MainchainBlockReferenceData] = ArrayBuffer()
    // Collect MainchainRefData considering the actor message processing timeout
    // Note: We may do a lot of websocket `getMainchainBlockReference` operations that are a bit slow,
    // because they are processed one by one, so we limit requests in time.
    val startTime: Long = System.currentTimeMillis()
    mainchainBlockReferenceDataToRetrieve.takeWhile(hash => {
      history.getMainchainBlockReferenceByHash(hash.data()).asScala match {
        case Some(ref) => {
          val refDataSize = ref.data.bytes.length + 4 // placeholder for MainchainReferenceData length
          if (blockSize + refDataSize > SidechainBlock.MAX_BLOCK_SIZE)
            false // stop data collection
          else {
            mainchainReferenceData.append(ref.data)
            blockSize += refDataSize
            // Note: temporary solution because of the delays on MC Websocket server part.
            // Can be after MC Websocket performance optimization.
            val isTimeout: Boolean = System.currentTimeMillis() - startTime >= timeout.duration.toMillis / 2
            !isTimeout // continue data collection
          }
        }
        case None => return null
      }
    })

    val transactions: Iterable[SidechainTypes#SCAT] =
      collectTransactionsFromMemPool(blockSize)

    val forgingStakeInfo: ForgingStakeInfo = new ForgingStakeInfo(
      new PublicKey25519Proposition(new Array[Byte](PublicKey25519Proposition.KEY_LENGTH)),
      new VrfPublicKey(new Array[Byte](VrfPublicKey.KEY_LENGTH)),
      0
    )

    val block = createNewBlock(
      getBranchPointInfo(history).get,
      false,
      parentBlockId,
      System.currentTimeMillis / 1000,
      mainchainReferenceData,
      transactions,
      mainchainHeaders,
      ommers,
      new PrivateKey25519(
        new Array[Byte](PrivateKey25519.PRIVATE_KEY_LENGTH),
        new Array[Byte](PrivateKey25519.PUBLIC_KEY_LENGTH)
      ),
      forgingStakeInfo,
      new VrfProof(new Array[Byte](VrfProof.PROOF_LENGTH)),
      new MerklePath(new JArrayList()),
      null
    )
    block.get.asInstanceOf[AccountBlock]
  }

}
