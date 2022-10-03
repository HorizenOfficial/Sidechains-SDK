package com.horizen.certificatesubmitter


import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.certnative.BackwardTransfer
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.params.NetworkParams
import com.horizen.storage.SidechainHistoryStorage
import com.horizen.websocket.client.MainchainNodeChannel
import scala.concurrent.ExecutionContext
import java.util.Optional
import scala.compat.java8.OptionConverters._
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
  type FPI = SidechainFeePaymentsInfo
  type HSTOR = SidechainHistoryStorage
  type VL = SidechainWallet
  type HIS = SidechainHistory
  type MS = SidechainState
  type MP = SidechainMemoryPool
  type PM = SidechainBlock


  override def getUtxoMerkleTreeRoot(referencedWithdrawalEpochNumber: Int, state: SidechainState): Optional[Array[Byte]] = {
    if (params.isCSWEnabled) {
      state.utxoMerkleTreeRoot(referencedWithdrawalEpochNumber) match {
        case x: Some[Array[Byte]] => x.asJava
        case None =>
          log.error("UtxoMerkleTreeRoot is not defined even if CSW is enabled")
          throw new IllegalStateException("UtxoMerkleTreeRoot is not defined")
      }
    }
    else {
      Optional.empty()
    }
  }

  override def getWithdrawalRequests(state: SidechainState, referencedEpochNumber: Int): Seq[BackwardTransfer] =
    state.withdrawalRequests(referencedEpochNumber).map(box => new BackwardTransfer(box.proposition.bytes, box.value))

}


object CertificateSubmitterRef {
  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props =

    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel)).withMailbox("akka.actor.deployment.submitter-prio-mailbox")


  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params, mainchainChannel).withMailbox("akka.actor.deployment.submitter-prio-mailbox"), name)
}
