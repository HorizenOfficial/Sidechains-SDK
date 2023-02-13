package com.horizen.account.storage

import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.utils.AccountBlockFeeInfo
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.WithdrawalEpochInfo
import sparkz.util.ModifierId

// expect this storage to be passed by the app during SidechainApp initialization
trait AccountStateMetadataStorageReader {

  def getWithdrawalEpochInfo: WithdrawalEpochInfo

  def getFeePayments(withdrawalEpochNumber: Int): Seq[AccountBlockFeeInfo]

  def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]

  def lastCertificateReferencedEpoch: Option[Int]

  def lastCertificateSidechainBlockId: Option[ModifierId]

  def getConsensusEpochNumber: Option[ConsensusEpochNumber]

  def getTransactionReceipt(txHash: Array[Byte]) : Option[EthereumReceipt]

  def hasCeased: Boolean

  // tip height
  def getHeight: Int

  // zero bytes when storage is empty
  def getAccountStateRoot: Array[Byte] // 32 bytes, kessack hash
}
