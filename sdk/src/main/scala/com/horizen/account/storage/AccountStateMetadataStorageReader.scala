package com.horizen.account.storage

import com.horizen.block.{WithdrawalEpochCertificate, WithdrawalEpochCertificateSerializer}
import com.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import com.horizen.utils.{BlockFeeInfo, ByteArrayWrapper, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer, Pair => JPair, _}

// expect this storage to be passed by the app during SidechainApp initialization
trait AccountStateMetadataStorageReader {

  def getWithdrawalEpochInfo: Option[WithdrawalEpochInfo]

  def getFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo]

  def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate]

  def getConsensusEpochNumber: Option[ConsensusEpochNumber]

  def hasCeased: Boolean

  // tip height
  def getHeight: Int

  // None only when the state is empty
  def getAccountStateRoot: Option[Array[Byte]] // 32 bytes, kessack hash
}
