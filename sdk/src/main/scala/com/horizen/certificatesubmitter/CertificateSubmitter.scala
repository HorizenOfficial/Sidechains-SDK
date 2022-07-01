package com.horizen.certificatesubmitter


import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.certnative.BackwardTransfer
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.websocket.client.MainchainNodeChannel

import scala.concurrent.ExecutionContext
import scala.language.postfixOps


class CertificateSubmitter(settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel)
                          (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock
  ](settings, sidechainNodeViewHolderRef, params, mainchainChannel) {
  type HSTOR = SidechainHistoryStorage
  type VL = SidechainWallet
  type HIS = SidechainHistory
  type MS = SidechainState
  type MP = SidechainMemoryPool
  type PM = SidechainBlock

  override def preStart(): Unit = {
    super.preStart()
  }

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
  }

  override def postStop(): Unit = {
    super.postStop()
  }

  override def getUtxoMerkleTreeRoot(state: SidechainState, referencedEpoch: Int): Array[Byte] =
    state.utxoMerkleTreeRoot(referencedEpoch).get

  override def getWithdrawalRequests(state: SidechainState, referencedEpochNumber: Int): Seq[BackwardTransfer] =
    state.withdrawalRequests(referencedEpochNumber).map(box => new BackwardTransfer(box.proposition.bytes, box.value))

}

object CertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props =
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel), name)
}
