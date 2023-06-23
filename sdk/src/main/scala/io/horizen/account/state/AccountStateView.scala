package io.horizen.account.state

import io.horizen.SidechainTypes
import io.horizen.account.state.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import io.horizen.account.state.receipt.{EthereumConsensusDataReceipt, EthereumReceipt}
import io.horizen.account.storage.AccountStateMetadataStorageView
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils._
import io.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import io.horizen.consensus.ConsensusEpochNumber
import io.horizen.state.StateView
import io.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import io.horizen.evm.StateDB
import sparkz.core.VersionTag
import sparkz.util.{ModifierId, SparkzLogging}

import java.math.BigInteger
import scala.util.Try

// this class extends 2 main hierarchies, which are kept separate:
//  - StateView (trait): metadata read/write
//      Implements the methods via metadataStorageView
//  - StateDbAccountStateView (concrete class) : evm stateDb read/write
//      Inherits its methods
class AccountStateView(
    metadataStorageView: AccountStateMetadataStorageView,
    stateDb: StateDB,
    messageProcessors: Seq[MessageProcessor]
) extends StateDbAccountStateView(stateDb, messageProcessors)
      with StateView[SidechainTypes#SCAT]
      with AutoCloseable
      with SparkzLogging {


  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def applyMessage(msg: Message, blockGasPool: GasPool, blockContext: BlockContext): Array[Byte] = {
    new StateTransition(this, messageProcessors, blockGasPool, blockContext).transition(msg)
  }


  /**
   * Possible outcomes:
   *   - tx applied succesfully => Receipt with status success
   *   - tx execution failed => Receipt with status failed
   *     - if any ExecutionFailedException was thrown, including but not limited to:
   *     - OutOfGasException (not intrinsic gas, see below!)
   *     - EvmException (EVM reverted) / native contract exception
   *   - tx could not be applied => throws an exception (this will lead to an invalid block)
   *     - any of the preChecks fail
   *     - not enough gas for intrinsic gas
   *     - block gas limit reached
   */
  def applyTransaction(
                        tx: SidechainTypes#SCAT,
                        txIndex: Int,
                        blockGasPool: GasPool,
                        blockContext: BlockContext
                      ): Try[EthereumConsensusDataReceipt] = Try {
    if (!tx.isInstanceOf[EthereumTransaction])
      throw new IllegalArgumentException(s"Unsupported transaction type ${tx.getClass.getName}")

    val ethTx = tx.asInstanceOf[EthereumTransaction]

    // It should never happen if the tx has been accepted in mempool.
    // In some negative test scenario this can happen when forcing an unsigned tx to be forged in a block.
    // In this case the 'from' attribute in the msg would not be
    // set, and it would be difficult to rootcause the reason why gas and nonce checks would fail
    if (!ethTx.isSigned)
      throw new IllegalArgumentException(s"Transaction is not signed: ${ethTx.id}")

    val txHash = BytesUtils.fromHexString(ethTx.id)
    val msg = ethTx.asMessage(blockContext.baseFee)

    // Tx context for stateDB, to know where to keep EvmLogs
    setupTxContext(txHash, txIndex)

    log.debug(s"applying msg: used pool gas ${blockGasPool.getUsedGas}")
    // apply message to state
    val status =
      try {
        applyMessage(msg, blockGasPool, blockContext)
        ReceiptStatus.SUCCESSFUL
      } catch {
        // any other exception will bubble up and invalidate the block
        case err: ExecutionFailedException =>
          log.debug(s"applying message failed, tx id: ${ethTx.id}, reason: ${err.getMessage}")
          ReceiptStatus.FAILED
      } finally {
        // finalize pending changes, clear the journal and reset refund counter
        stateDb.finalizeChanges()
      }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(
      ethTx.version(),
      status.id,
      blockGasPool.getUsedGas,
      getLogs(txHash)
    )
    log.debug(s"Returning consensus data receipt: ${consensusDataReceipt.toString()}")
    log.debug(s"applied msg: used pool gas ${blockGasPool.getUsedGas}")

    consensusDataReceipt
  }

  def addTopQualityCertificates(refData: MainchainBlockReferenceData, blockId: ModifierId): Unit = {
    refData.topQualityCertificate.foreach(cert => {
      log.debug(s"adding top quality cert to state: $cert.")
      updateTopQualityCertificate(cert, blockId)
    })
  }

  // out-of-the-box helpers
  override def updateTopQualityCertificate(cert: WithdrawalEpochCertificate, blockId: ModifierId): Unit = {
    metadataStorageView.updateTopQualityCertificate(cert)
    metadataStorageView.updateLastCertificateReferencedEpoch(cert.epochNumber)
    metadataStorageView.updateLastCertificateSidechainBlockIdOpt(blockId)
  }

  override def updateFeePaymentInfo(info: AccountBlockFeeInfo): Unit = {
    metadataStorageView.updateFeePaymentInfo(info)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit =
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit =
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)

  override def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit =
    metadataStorageView.updateTransactionReceipts(receipts)

  def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] =
    metadataStorageView.getTransactionReceipt(txHash)

  def updateNextBaseFee(baseFee: BigInteger): Unit = metadataStorageView.updateNextBaseFee(baseFee)

  def getNextBaseFee: BigInteger = metadataStorageView.getNextBaseFee

  override def setCeased(): Unit = metadataStorageView.setCeased()

  override def commit(version: VersionTag): Unit = {
    // Update StateDB without version, then set the rootHash and commit metadataStorageView
    val rootHash = stateDb.commit()
    metadataStorageView.updateAccountStateRoot(rootHash.toBytes)
    metadataStorageView.commit(version)
  }

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = metadataStorageView.getWithdrawalEpochInfo

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  // useful in bootstrapping tool
  def getConsensusEpochNumberAsInt: Int = getConsensusEpochNumber.getOrElse(0)

  override def getFeePaymentsInfo(
      withdrawalEpoch: Int,
      blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None
  ): Seq[AccountPayment] = {
    var blockFeeInfoSeq = metadataStorageView.getFeePayments(withdrawalEpoch)
    blockToAppendFeeInfo.foreach(blockFeeInfo => blockFeeInfoSeq = blockFeeInfoSeq :+ blockFeeInfo)
    AccountFeePaymentsUtils.getForgersRewards(blockFeeInfoSeq)
  }

  override def getAccountStateRoot: Array[Byte] = metadataStorageView.getAccountStateRoot
}
