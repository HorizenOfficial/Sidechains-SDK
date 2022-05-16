package com.horizen.state

import com.horizen.block.{MainchainBlockReferenceData, SidechainBlock, WithdrawalEpochCertificate}
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.transaction.Transaction
import com.horizen.utils.{BlockFeeInfo, WithdrawalEpochInfo}
import scorex.core.VersionTag
import scorex.core.serialization.ScorexSerializer

import scala.util.{Failure, Success, Try}


abstract class AccountTransaction extends Transaction {
  override type M = this.type
  override def transactionTypeId: Byte = ???

  override def version: Byte = ???

  override val messageToSign: Array[Byte] = ???

  override def serializer: ScorexSerializer[AccountTransaction.this.type] = ???

  def from()
  // Account interface
  // from
  // signature
  // to
  // input
  // etc..
}

case class Account(nonce: Long,
                   balance: Long,
                   codeHash: Array[Byte],
                   storageRoot: Array[Byte])


trait AccountStateReader extends StateReader {
  def getAccount(address: Array[Byte]): Account
  def getBalance(address: Array[Byte]): Long
  // etc.
}



class AccountStateView[SV <: AccountStateView[SV]] extends StateView[AccountTransaction, SV] with AccountStateReader {
  view: SV =>
  override type NVCT = this.type

  // modifiers
  override def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[SV] = ???

  override def applyTransaction(tx: AccountTransaction): Try[SV] = ???

  // account modifiers:
  def addAccount(address: Array[Byte], account: Account): Try[SV] = ???
  def addBalance(address: Array[Byte], amount: Long) : Try[SV] = ???
  def subBalance(address: Array[Byte], amount: Long) : Try[SV] = ???
  def updateAccountStorageRoot(address: Array[Byte], root: Array[Byte]) : Try[SV] = ???

  // out-of-the-box helpers
  override def addCertificate(cert: WithdrawalEpochCertificate): Try[SV] = ???

  override def addWithdrawalRequest(wrb: WithdrawalRequestBox): Try[SV] = ???

  override def delegateStake(fb: ForgerBox): Try[SV] = ???

  override def spendStake(fb: ForgerBox): Try[SV] = ???

  override def addFeeInfo(info: BlockFeeInfo): Try[SV] = ???

  // view controls
  override def savepoint(): Unit = ???

  override def rollbackToSavepoint(): Try[SV] = ???

  override def commit(version: VersionTag): Try[Unit] = ???


  // validate only part
  override def validate(tx: AccountTransaction): Try[Unit] = ???

  override def validate(mod: SidechainBlock): Try[Unit] = ???

  // versions part
  override def version: VersionTag = ???

  override def maxRollbackDepth: Int = ???

  // getters
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = ???

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = ???

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = ???

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = ???

  override def hasCeased: Boolean = ???

  // account specific getters
  override def getAccount(address: Array[Byte]): Account = ???

  override def getBalance(address: Array[Byte]): Long = ???
}


class AccountState[SV <: AccountStateView[SV], S <: AccountState[SV, S]]
  extends State[AccountTransaction, SV, S]
    with AccountStateReader {
   self: S =>

  // Modifiers:
  override def applyModifier(mod: SidechainBlock): Try[S] = ???

  override def rollbackTo(version: VersionTag): Try[S] = ???

  // versions part
  override def version: VersionTag = ???

  override def maxRollbackDepth: Int = ???

  // View
  override def getView: SV = ???

  // getters:
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = ???

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = ???

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = ???

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = ???

  override def hasCeased: Boolean = ???

  // Account specific getters
  override def getAccount(address: Array[Byte]): Account = ???

  override def getBalance(address: Array[Byte]): Long = ???
}



// Types tests:

trait FinalAccountStateReader extends AccountStateReader {
  def getSomething: Int = 42
}

class FinalAccountView extends AccountStateView[FinalAccountView] with FinalAccountStateReader

class FinalState extends AccountState[FinalAccountView, FinalState] with FinalAccountStateReader {
  override def getReader: FinalAccountStateReader = this
}


object Main extends App {
  val state: FinalState = new FinalState
  test2()

  def test1(): Unit = {
    val view: FinalAccountView = state.getView
    view.savepoint()
    view.applyMainchainBlockReferenceData(null) match {
      case Success(v) =>
        val newVersion: String = "v1"
        v.commit(VersionTag @@ newVersion)
      case Failure(exception) =>
        view.rollbackToSavepoint()
    }
  }

  def test2(): Unit = {
    val reader: FinalAccountStateReader = state.getReader
    val res: Int = reader.getSomething
    System.out.println(res)
  }

}