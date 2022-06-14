package com.horizen.account.state

import com.horizen.SidechainTypes
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.block.{MainchainBlockReferenceData, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.consensus.{ConsensusEpochInfo, ConsensusEpochNumber}
import com.horizen.state.StateView
import com.horizen.utils.{BlockFeeInfo, MerkleTree, WithdrawalEpochInfo}
import scorex.core.VersionTag
import scorex.util.{ModifierId, bytesToId}

import scala.util.Try

class AccountStateView(metadataStorageView: AccountStateMetadataStorageView, messageProcessors: Seq[MessageProcessor]) extends StateView[SidechainTypes#SCAT, AccountStateView] with AccountStateReader {
  view: AccountStateView =>

  override type NVCT = this.type

  // modifiers
  override def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[AccountStateView] = Try {
    // TODO
    this
  }

  def setupTxContext(tx: EthereumTransaction): Unit = {
    // TODO
  }

  override def applyTransaction(tx: SidechainTypes#SCAT): Try[AccountStateView] = Try {
    if(tx.isInstanceOf[EthereumTransaction]) {
      val ethTx = tx.asInstanceOf[EthereumTransaction]
      setupTxContext(ethTx)
      val message: Message = Message.fromTransaction(ethTx)
      val processor = messageProcessors.find(_.canProcess(message, this)).getOrElse(
        throw new IllegalArgumentException(s"Transaction ${ethTx.id} has no known processor.")
      )
      processor.process(message, this) match {
        case success: ExecutionSucceeded => this // TODO
        case failed: ExecutionFailed => this // TODO
        case invalid: InvalidMessage => throw new Exception(s"Transaction ${ethTx.id} is invalid.", invalid.getReason)
      }
    } else
      throw new IllegalArgumentException(s"Unsupported transaction type ${tx.getClass.getName}")
  }

  // account modifiers:
  def addAccount(address: Array[Byte], account: Account): Try[AccountStateView] = ???

  def addBalance(address: Array[Byte], amount: Long): Try[AccountStateView] = ???

  def subBalance(address: Array[Byte], amount: Long): Try[AccountStateView] = ???

  protected def updateAccountStorageRoot(address: Array[Byte], root: Array[Byte]): Try[AccountStateView] = ???

  def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit] =
  {
    //stateDb-->
    //public void setStorage(byte[] address, byte[] key, byte[] value) throws Exception {
    ???
  }

  def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit] =
  {
    //stateDb-->
    //public void setStorageBytes(byte[] address, byte[] key, byte[] value) throws Exception {
    ???
  }

  def getAccountStorage(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]] = {
    // it should be legal to have a valid address with no values for the given key
    // It should throw an exception if the address does not exist
    ???
  }

  def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]] = {
    // it should be legal to have a valid address with no values for the given key
    // It should throw an exception if the address does not exist
    ???
    // getStorageBytes--> handling values longer than 32 bytes
  }

  // log handling
  // def addLog(log: EvmLog) : Try[Unit] = ???

  // out-of-the-box helpers
  override def addCertificate(cert: WithdrawalEpochCertificate): Try[AccountStateView] = Try {
    metadataStorageView.updateTopQualityCertificate(cert)
    new AccountStateView(metadataStorageView, messageProcessors)
  }

  override def addWithdrawalRequest(wrb: WithdrawalRequestBox): Try[AccountStateView] = ???

  override def delegateStake(fb: ForgerBox): Try[AccountStateView] = ???

  override def spendStake(fb: ForgerBox): Try[AccountStateView] = ???

  // note: probably must be "set" than "add". Because we allow it only once per "commit".
  override def addFeeInfo(info: BlockFeeInfo): Try[AccountStateView] = Try {
    metadataStorageView.addFeePayment(info)
    new AccountStateView(metadataStorageView, messageProcessors)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Try[AccountStateView] = Try {
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)
    new AccountStateView(metadataStorageView, messageProcessors)
  }

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Try[AccountStateView] = Try {
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)
    new AccountStateView(metadataStorageView, messageProcessors)
  }

  def updateAccountStateRoot(accountStateRoot: Array[Byte]): Try[AccountStateView] = Try {
    metadataStorageView.updateAccountStateRoot(accountStateRoot)
    new AccountStateView(metadataStorageView, messageProcessors)
  }

  override def setCeased(): Try[AccountStateView] = Try {
    metadataStorageView.setCeased()
    new AccountStateView(metadataStorageView, messageProcessors)
  }

  // view controls
  override def savepoint(): Unit = ???

  override def rollbackToSavepoint(): Try[AccountStateView] = ???

  override def commit(version: VersionTag): Try[Unit] = Try {
    // Update StateDB without version, then commit metadataStorageView
    metadataStorageView.commit(version)
  }

  // versions part
  override def version: VersionTag = ???

  override def maxRollbackDepth: Int = ???

  // getters
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = ???

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)
  }

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch) match {
      case Some(certificate) => certificate.quality
      case None => 0
    }
  }

  // get the record of storage or return WithdrawalEpochInfo(0,0) if state is empty
  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    metadataStorageView.getWithdrawalEpochInfo.getOrElse(WithdrawalEpochInfo(0, 0))
  }

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getBlockFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = {
    metadataStorageView.getFeePayments(withdrawalEpochNumber)
  }

  // account specific getters
  override def getAccount(address: Array[Byte]): Account = ???

  override def getBalance(address: Array[Byte]): Long = ???

  override def getAccountStateRoot: Option[Array[Byte]] = metadataStorageView.getAccountStateRoot

}
