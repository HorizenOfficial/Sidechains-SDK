package com.horizen.account.storage

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.storage.Storage
import com.horizen.utils.{BlockFeeInfo, ByteArrayWrapper, WithdrawalEpochInfo}
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.Try

// expect this storage to be passed by the app during SidechainApp initialization
class AccountStateMetadataStorage(storage: Storage) extends AccountStateMetadataStorageReader with ScorexLogging {
  def getView: AccountStateMetadataStorageView = new AccountStateMetadataStorageView(storage)

  def lastVersionId: Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions: Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback(version: ByteArrayWrapper): Try[AccountStateMetadataStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty

  override def getWithdrawalEpochInfo: Option[WithdrawalEpochInfo] = getView.getWithdrawalEpochInfo

  override def getFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = getView.getFeePayments(withdrawalEpochNumber)

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = getView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = getView.getConsensusEpochNumber

  override def hasCeased: Boolean = getView.hasCeased

  override def getHeight: Int = getView.getHeight

  override def getAccountStateRoot: Option[Array[Byte]] = getView.getAccountStateRoot
}
