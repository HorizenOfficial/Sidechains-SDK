package com.horizen.state

import com.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment}
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.WithdrawalEpochInfo
import java.math.BigInteger

trait BaseStateReader {
  def getWithdrawalEpochInfo: WithdrawalEpochInfo
  def getFeePaymentsInfo(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment]
  def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]
  def getConsensusEpochNumber: Option[ConsensusEpochNumber]
  def hasCeased: Boolean
  def getAccountStateRoot: Array[Byte] // 32 bytes, keccak hash
  //Contains the base fee to be used when forging the next block
  def getNextBaseFee: BigInteger
}
