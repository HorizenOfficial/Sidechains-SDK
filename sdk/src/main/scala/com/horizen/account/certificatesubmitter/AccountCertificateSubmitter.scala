package com.horizen.account.certificatesubmitter

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.wallet.AccountWallet
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter
import com.horizen.params.NetworkParams
import com.horizen.websocket.client.MainchainNodeChannel

import scala.concurrent.ExecutionContext
import scala.language.postfixOps


class AccountCertificateSubmitter(settings: SidechainSettings,
                                  sidechainNodeViewHolderRef: ActorRef,
                                  params: NetworkParams,
                                  mainchainChannel: MainchainNodeChannel)
                                 (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock
  ](settings, sidechainNodeViewHolderRef, params, mainchainChannel) {
  type HSTOR = AccountHistoryStorage
  type VL = AccountWallet
  type HIS = AccountHistory
  type MS = AccountState
  type MP = AccountMemoryPool
  type PM = AccountBlock

  override def preStart(): Unit = {
    super.preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
  }

  override def postStop(): Unit = {
    super.postStop()
  }

  override def getUtxoMerkleTreeRoot(state: AccountState, referencedEpoch: Int): Array[Byte] =
    new Array[Byte](0)

  override def getWithdrawalRequests(state: AccountState, referencedEpochNumber: Int): Seq[WithdrawalRequestBox] =
    state.withdrawalRequests(referencedEpochNumber)
}

object AccountCertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props =
    Props(new AccountCertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel), name)
}
