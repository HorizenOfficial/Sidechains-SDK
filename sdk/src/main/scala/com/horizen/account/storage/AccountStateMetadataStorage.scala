package com.horizen.account.storage

import com.horizen.account.receipt.EthereumReceipt
import com.horizen.account.utils.AccountBlockFeeInfo
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.storage.{SidechainStorageInfo, Storage}
import com.horizen.utils.{ByteArrayWrapper, WithdrawalEpochInfo}
import sparkz.util.{ModifierId, SparkzLogging}

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.Try

// expect this storage to be passed by the app during SidechainApp initialization
class AccountStateMetadataStorage(storage: Storage)
  extends AccountStateMetadataStorageReader with SidechainStorageInfo with SparkzLogging
{
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

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = getView.getWithdrawalEpochInfo

  override def getFeePayments(withdrawalEpochNumber: Int): Seq[AccountBlockFeeInfo] = getView.getFeePayments(withdrawalEpochNumber)

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = getView.getTopQualityCertificate(referencedWithdrawalEpoch)

  override def lastCertificateReferencedEpoch: Option[Int] = getView.lastCertificateReferencedEpoch

  override def lastCertificateSidechainBlockId: Option[ModifierId] = getView.lastCertificateSidechainBlockId

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = getView.getConsensusEpochNumber

  override def hasCeased: Boolean = getView.hasCeased

  override def getHeight: Int = getView.getHeight

  override def getAccountStateRoot: Array[Byte] = getView.getAccountStateRoot

  override def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] = getView.getTransactionReceipt(txHash)

}
