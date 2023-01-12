package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils._
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.evm.StateDB
import com.horizen.state.StateView
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.core.VersionTag
import scorex.util.ScorexLogging
import scorex.util.ModifierId
import java.math.BigInteger

// this class extends 2 main hierarchies, which are kept separate:
//  - StateView (trait): metadata read/write
//      Implements the methods via metadataStorageView
//  - StateDbAccountStateView (concrete class) : evm stateDb read/write
//      Inherits its methods
class AccountStateView(
  metadataStorageView: AccountStateMetadataStorageView,
  stateDb: StateDB,
  messageProcessors: Seq[MessageProcessor])
  extends StateDbAccountStateView(stateDb, messageProcessors)
    with StateView[SidechainTypes#SCAT]
    with AutoCloseable
    with ScorexLogging {


  def addTopQualityCertificates(refData: MainchainBlockReferenceData, blockId: ModifierId): Unit = {
    refData.topQualityCertificate.foreach(cert => {
      log.debug(s"adding top quality cert to state: $cert.")
      updateTopQualityCertificate(cert, blockId)
    })
  }

  override def getAccountStorage(address: Array[Byte], key: Array[Byte]): Array[Byte] =
    stateDb.getStorage(address, key)

  override def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit =
    stateDb.setStorage(address, key, value)

  final override def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Unit =
    updateAccountStorage(address, key, null)

  // random data used to salt chunk keys in the storage trie when accessed via get/updateAccountStorageBytes
  private val chunkKeySalt =
    BytesUtils.fromHexString("fa09428dd8121ea57327c9f21af74ffad8bfd5e6e39dc3dc6c53241a85ec5b0d")

  // chunk keys are generated by hashing a salt, the original key and the chunk index
  // the salt was added to reduce the risk of accidental hash collisions because similar strategies
  // to generate storage keys might be used by the caller
  private def getChunkKey(key: Array[Byte], chunkIndex: Int): Array[Byte] =
    Keccak256.hash(chunkKeySalt, key, BigIntegerUtil.toUint256Bytes(BigInteger.valueOf(chunkIndex)))

  final override def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val length = new BigInteger(1, getAccountStorage(address, key)).intValueExact()
    val data = new Array[Byte](length)
    for (chunkIndex <- 0 until (length + Hash.LENGTH - 1) / Hash.LENGTH) {
      getAccountStorage(address, getChunkKey(key, chunkIndex)).copyToArray(data, chunkIndex * Hash.LENGTH)
    }
    data
  }

  final override def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Unit = {
    // get previous length of value stored, if any
    val oldLength = new BigInteger(1, getAccountStorage(address, key)).intValueExact()
    // values are split up into 32-bytes chunks:
    // the length of the value is stored at the original key and the chunks are stored at hash(key, i)
    val newLength = value.length
    // if the new value is empty remove all key-value pairs, including the one holding the value length
    updateAccountStorage(address, key, BigIntegerUtil.toUint256Bytes(BigInteger.valueOf(newLength)))
    for (start <- 0 until Math.max(newLength, oldLength) by Hash.LENGTH) {
      val chunkIndex = start / Hash.LENGTH
      val chunkKey = getChunkKey(key, chunkIndex)
      if (start < newLength) {
        // (over-)write chunks
        updateAccountStorage(address, chunkKey, value.slice(start, start + Hash.LENGTH).padTo(Hash.LENGTH, 0.toByte))
      } else {
        // remove previous chunks that are not needed anymore
        removeAccountStorage(address, chunkKey)
      }
    }
  }

  final override def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Unit =
    updateAccountStorageBytes(address, key, Array.empty)

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
    metadataStorageView.updateAccountStateRoot(rootHash)
    metadataStorageView.commit(version)
  }

  // getters

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] =
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = metadataStorageView.getWithdrawalEpochInfo

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getFeePaymentsInfo(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment] = {
    var blockFeeInfoSeq = metadataStorageView.getFeePayments(withdrawalEpoch)
    blockToAppendFeeInfo.foreach(blockFeeInfo => blockFeeInfoSeq = blockFeeInfoSeq :+ blockFeeInfo)
    AccountFeePaymentsUtils.getForgersRewards(blockFeeInfoSeq)
  }

  override def getAccountStateRoot: Array[Byte] = metadataStorageView.getAccountStateRoot

  override def getGasTrackedView(gas: GasPool): BaseAccountStateView =
    new AccountStateViewGasTracked(metadataStorageView, stateDb, messageProcessors, gas)
}
