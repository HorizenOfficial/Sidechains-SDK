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
import scala.reflect.ClassTag


class CertificateSubmitter(settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel)
                          (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter(settings, sidechainNodeViewHolderRef, params, mainchainChannel) {

  override type TX = SidechainTypes#SCBT
  override type H = SidechainBlockHeader
  override type PM = SidechainBlock

  override type FPI = SidechainFeePaymentsInfo
  override type HSTOR = SidechainHistoryStorage
  override type VL = SidechainWallet
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type MP = SidechainMemoryPool

  override implicit val tag: ClassTag[PM] = ClassTag[PM](classOf[PM])

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
