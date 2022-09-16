package com.horizen.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.WithdrawalRequest
import com.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment}
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.WithdrawalEpochInfo

import java.math.BigInteger

trait BaseStateReader {
  def getWithdrawalEpochInfo: WithdrawalEpochInfo

  def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest]

  def getFeePayments(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment]

  def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]

  def certificateTopQuality(referencedWithdrawalEpoch: Int): Long

  def getConsensusEpochNumber: Option[ConsensusEpochNumber]

  def hasCeased: Boolean
}
