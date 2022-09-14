package com.horizen.state

import com.horizen.account.state.WithdrawalRequest
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}

trait BaseStateReader {
  def getWithdrawalEpochInfo: WithdrawalEpochInfo
  
  def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest]

  def getFeePayments(withdrawalEpoch: Int): Seq[BlockFeeInfo]

  def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]

  def certificateTopQuality(referencedWithdrawalEpoch: Int): Long

  def getConsensusEpochNumber: Option[ConsensusEpochNumber]

  def hasCeased: Boolean
}
