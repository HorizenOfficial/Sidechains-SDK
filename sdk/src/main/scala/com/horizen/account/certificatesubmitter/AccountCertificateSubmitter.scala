package com.horizen.account.certificatesubmitter

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.wallet.AccountWallet
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter
import com.horizen.certnative.BackwardTransfer
import com.horizen.params.NetworkParams
import com.horizen.websocket.client.MainchainNodeChannel

import java.util.Optional
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.reflect.ClassTag


class AccountCertificateSubmitter(settings: SidechainSettings,
                                  sidechainNodeViewHolderRef: ActorRef,
                                  params: NetworkParams,
                                  mainchainChannel: MainchainNodeChannel)
                                 (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel) {


  override type TX = SidechainTypes#SCAT
  override type H = AccountBlockHeader
  override type PM = AccountBlock

  override type FPI = AccountFeePaymentsInfo
  override type HSTOR = AccountHistoryStorage
  override type VL = AccountWallet
  override type HIS = AccountHistory
  override type MS = AccountState
  override type MP = AccountMemoryPool

  override implicit val tag: ClassTag[PM] = ClassTag[PM](classOf[PM])

  override def getUtxoMerkleTreeRoot(referencedEpoch: Int, state: AccountState): Optional[Array[Byte]] = Optional.empty()

  override def getWithdrawalRequests(state: AccountState, referencedEpochNumber: Int): Seq[BackwardTransfer] =
    state.withdrawalRequests(referencedEpochNumber).map(request => new BackwardTransfer(request.proposition.bytes, request.valueInZennies))

}

object AccountCertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props =
    Props(new AccountCertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel))
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox")

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel)
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox"))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel)
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox"), name)
}
