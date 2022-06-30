package com.horizen.account.storage

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}

// expect this storage to be passed by the app during SidechainApp initialization
trait AccountStateMetadataStorageReader {

  def getWithdrawalEpochInfo: WithdrawalEpochInfo

  def getFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo]

  def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]

  def getConsensusEpochNumber: Option[ConsensusEpochNumber]

  def hasCeased: Boolean

  // tip height
  def getHeight: Int

  // None only when the state is empty
  def getAccountStateRoot: Option[Array[Byte]] // 32 bytes, kessack hash
}
