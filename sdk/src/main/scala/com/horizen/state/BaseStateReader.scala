package com.horizen.state

import com.horizen.account.state.WithdrawalRequest
import com.horizen.account.utils.{AccountBlockFeeInfo, AccountPayment}
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.WithdrawalEpochInfo

trait BaseStateReader {
  def getWithdrawalEpochInfo: WithdrawalEpochInfo

  def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest]

  def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof]

  def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys]

  def lastCertificateReferencedEpoch: Option[Int]

  def getFeePayments(withdrawalEpoch: Int, blockToAppendFeeInfo: Option[AccountBlockFeeInfo] = None): Seq[AccountPayment]

  def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]

  def certificateTopQuality(referencedWithdrawalEpoch: Int): Long

  def getConsensusEpochNumber: Option[ConsensusEpochNumber]

  def hasCeased: Boolean
}
