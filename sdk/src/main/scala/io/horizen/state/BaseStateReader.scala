package io.horizen.state

import io.horizen.account.state.receipt.EthereumReceipt
import io.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment}
import io.horizen.block.WithdrawalEpochCertificate
import io.horizen.consensus.ConsensusEpochNumber
import io.horizen.utils.WithdrawalEpochInfo

import java.math.BigInteger

trait BaseStateReader {
  def getWithdrawalEpochInfo: WithdrawalEpochInfo
  def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]
  def getTopCertificateMainchainHash(referencedWithdrawalEpoch: Int): Option[Array[Byte]] //hash of mainchain block that published the top quality cert of this epoch
  def getFeePaymentsInfo(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment]
  def getConsensusEpochNumber: Option[ConsensusEpochNumber]
  def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt]
  def getNextBaseFee: BigInteger //Contains the base fee to be used when forging the next block
  def hasCeased: Boolean
  def getAccountStateRoot: Array[Byte] // 32 bytes, keccak hash
}
