package com.horizen.network

import akka.actor.{ActorRef, ActorRefFactory, Props, Timers}
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.certificatesubmitter.CertificateSubmitter.CertificateSignatureInfo
import com.horizen.network.SidechainNodeViewSynchronizer.InternalReceivableMessages.SetSyncAsDone
import com.horizen.network.SidechainNodeViewSynchronizer.Timers.SyncIsDoneTimer
import com.horizen.validation.{BlockInFutureException, InconsistentDataException}
import scorex.core.consensus.History.{Fork, Nonsense, Older, Unknown, Younger}
import scorex.core.consensus.{History, HistoryReader, SyncInfo}
import scorex.core.network.NetworkControllerSharedMessages.ReceivableMessages.DataFromPeer
import scorex.core.network.{ConnectedPeer, ModifiersStatus, NodeViewSynchronizer, PeerConnectionHandlerRef, SendToPeers, SyncTracker}
import scorex.core.network.NodeViewSynchronizer.Events
import scorex.core.network.NodeViewSynchronizer.Events.{BetterNeighbourAppeared, NoBetterNeighbour}
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{SemanticallyFailedModification, SemanticallySuccessfulModifier, SyntacticallyFailedModification}
import scorex.core.network.message.{InvData, InvSpec, Message, SyncInfoMessageSpec}
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.NetworkSettings
import scorex.core.transaction.{MempoolReader, Transaction}
import scorex.core.utils.NetworkTimeProvider
import scorex.core.{ModifierTypeId, NodeViewModifier, PersistentNodeViewModifier, idsToString}
import scorex.util.ModifierId

import scala.Some
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.reflect.ClassTag


class SidechainNodeViewSynchronizer[TX <: Transaction,
  SI <: SidechainSyncInfo,
  SIS <: SyncInfoMessageSpec[SI],
  PMOD <: PersistentNodeViewModifier,
  HR <: HistoryReader[PMOD, SI] : ClassTag,
  MR <: MempoolReader[TX] : ClassTag]
  (networkControllerRef: ActorRef,
  viewHolderRef: ActorRef,
  syncInfoSpec: SidechainSyncInfoMessageSpec.type,
  networkSettings: NetworkSettings,
  timeProvider: NetworkTimeProvider,
  modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])
  (implicit ec: ExecutionContext)
  extends NodeViewSynchronizer[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
    SidechainBlock, SidechainHistory, SidechainMemoryPool](networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers)
    with Timers {

  override protected val deliveryTracker = new SidechainDeliveryTracker(context.system, deliveryTimeout, maxDeliveryChecks, self)
  override protected val statusTracker = new SidechainSyncTracker(self, context, networkSettings, timeProvider)

  protected var chainIsOnSync = false
  protected val delay = calculateDelayForTimer(5)

  case class OtherNodeSyncStatus[SI <: SyncInfo](remote: ConnectedPeer,
                                                 status: SidechainSyncStatus, // CHANGE THIS
                                                 extension: Seq[(ModifierTypeId, ModifierId)])

  private val onSyntacticallyFailedModifier: Receive = {

    //DIRAC TODO manage the StatusesMAp

    case SyntacticallyFailedModification(mod, exception) =>
      exception match {
        case _: BlockInFutureException =>
          // When next time NodeViewSynchronizer.processInv will be emitted for mod.id it will be processed again.
          // So no ban for mod.id
          deliveryTracker.setUnknown(mod.id)
        case _: InconsistentDataException =>
          // Try to ban the sender only (in case of modifier from remote)
          val peerOpt = deliveryTracker.peerInfo(mod.id)
          deliveryTracker.setUnknown(mod.id)
          peerOpt.foreach(penalizeMisbehavingPeer)
        case _ => // InvalidBlockException, InvalidSidechainBlockHeaderException and all other exceptions
          // Ban both mod.id and peer
          deliveryTracker.setInvalid(mod.id).foreach(penalizeMisbehavingPeer)
      }
  }

  private val onSemanticallyFailedModifier: Receive = {

    //DIRAC TODO manage the StatusesMAp cancel the stuff down here

    case SemanticallyFailedModification(mod, exception) =>
      exception match {
        case _: BlockInFutureException =>
          // When next time NodeViewSynchronizer.processInv will be emitted for mod.id it will be processed again.
          // So no ban for mod.id
          deliveryTracker.setUnknown(mod.id)
        case _: InconsistentDataException =>
          // Try to ban the sender only (in case of modifier from remote)
          val peerOpt = deliveryTracker.peerInfo(mod.id)
          deliveryTracker.setUnknown(mod.id)
          peerOpt.foreach(penalizeMisbehavingPeer)
        case _ => // InvalidBlockException, InvalidSidechainBlockHeaderException and all other exceptions
          // Ban both mod.id and peer
          deliveryTracker.setInvalid(mod.id).foreach(penalizeMisbehavingPeer)
      }
  }



  private val onSemanticallySuccessfulModifier: Receive = {
    case SemanticallySuccessfulModifier(pmod) =>
      val aPeer = wasRequested(pmod)
      aPeer match {
        case Some(peer) =>
          statusTracker.updateStatusWithLastSyncTime(peer,timeProvider.time())
          chainIsOnSync=true
          restartTimer
       case None =>
      }
  }

  override def receive: Receive = {
    onDownloadRequest orElse
    getLocalSyncInfo orElse
    processSync orElse
    processSyncStatus orElse
    processInv orElse
    modifiersReq orElse
    responseFromLocal orElse
    modifiersFromRemote orElse
    viewHolderEvents orElse
    peerManagerEvents orElse
    checkDelivery orElse
    setSyncAsDone orElse
    betterNeighboursEvents orElse{
      case a: Any => log.error("Strange input: " + a)
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[Events.NodeViewSynchronizerEvent])
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[PMOD]])
  }

  // Uses SidechainSyncStatus instead of status (ComparisonResult) that parent class does
  override   protected def processSync: Receive = {
    case DataFromPeer(spec, syncInfo: SI@unchecked, remote)
      if spec.messageCode == syncInfoSpec.messageCode =>
      log.info(s"  syncInfoSpec,   processSync: syncInfo = ${syncInfo}")
      log.info(s"historyReaderOpt = ${historyReaderOpt.toSeq.toString()}")
      historyReaderOpt match {
        case Some(historyReader) =>
          val ext = historyReader.continuationIds(syncInfo, networkSettings.desiredInvObjects)
          val comparison = historyReader.compare(syncInfo)
          log.info(s"Comparison with $remote having starting points ${idsToString(syncInfo.startingPoints)}. " +
            s"Comparison result is $comparison. Sending extension of length ${ext.length}")
          log.info(s"Extension ids: ${idsToString(ext)}")

          if (!(ext.nonEmpty || comparison != Younger))
            log.warn("Extension is empty while comparison is younger")
          log.info(s"calling OtherNodeSyncStatus, on myself ${self.toString()}")

          self ! OtherNodeSyncStatus(remote, SidechainSyncStatus(comparison,syncInfo.chainHeight), ext )
        case _ =>
      }
  }

  //view holder is telling other node status
  // Uses SidechainSyncStatus instead of status (ComparisonResult) that parent class does
  override protected def processSyncStatus: Receive = {
    case OtherNodeSyncStatus(remote, status, ext) =>
      statusTracker.updateSyncStatus(remote, status)

      status.historyCompare match {
        case Unknown =>
          //todo: should we ban peer if its status is unknown after getting info from it?
          log.warn("Peer status is still unknown")
        case Nonsense =>
          log.warn("Got nonsense")
        case Younger | Fork =>
          log.info(s"I'm AHEAD, I'm Sending Exytensions : ${ext} and Status = ${status} ")
          sendExtension(remote, status.historyCompare, ext)
        case _ => // does nothing for `Equal` and `Older`
          log.info("case _   SENDER is equal or older ")
      }
  }

  protected def betterNeighboursEvents: Receive = {
    case BetterNeighbourAppeared =>
      log.info("received a good neighbour ")

    case NoBetterNeighbour =>
      log.info("No better neighbours around ")
  }

  def wasRequested(pmod: PersistentNodeViewModifier): Option[ConnectedPeer] = {
    val peerToWhichIRequested= deliveryTracker.modHadBeenRequestedFromPeer(pmod.id,pmod.modifierTypeId)
    val peerFromWhichIreceived = deliveryTracker.modHadBeenReceivedFromPeer(pmod.id,pmod.modifierTypeId)
    if (peerToWhichIRequested == peerFromWhichIreceived && peerToWhichIRequested.get != None && peerFromWhichIreceived.get!=None)
      peerToWhichIRequested
    else
      None
  }

  protected def restartTimer()= {
    cancelTimerForSyncIsDone()
    startTimerForSyncIsDone()
  }

  def calculateDelayForTimer(param: Int) = {
    12 // let's observe after with big numbers
  }

  protected def startTimerForSyncIsDone(): Unit ={
    timers.startSingleTimer(SyncIsDoneTimer, SetSyncAsDone, FiniteDuration(delay, SECONDS))
  }

  protected def cancelTimerForSyncIsDone(): Unit={
       timers.cancel(SyncIsDoneTimer)
       log.info("I'm Syncing . . .")
  }

  protected def setSyncAsDone:Receive  ={
    case SetSyncAsDone =>
    chainIsOnSync=false
    log.info("I stop Synging . . .")
  }

  override protected def viewHolderEvents: Receive = {
    onSemanticallySuccessfulModifier orElse
    onSemanticallyFailedModifier orElse
    onSyntacticallyFailedModifier orElse
    super.viewHolderEvents
  }
}



object SidechainNodeViewSynchronizer {
  def props(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])
           (implicit ex: ExecutionContext): Props =
    Props(new SidechainNodeViewSynchronizer(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings,
      timeProvider, modifierSerializers))

  def apply(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]])
           (implicit context: ActorRefFactory, ex: ExecutionContext): ActorRef =
    context.actorOf(props(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers))

  def apply(networkControllerRef: ActorRef,
            viewHolderRef: ActorRef,
            syncInfoSpec: SidechainSyncInfoMessageSpec.type,
            networkSettings: NetworkSettings,
            timeProvider: NetworkTimeProvider,
            modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]],
            name: String)
           (implicit context: ActorRefFactory, ex: ExecutionContext): ActorRef =
    context.actorOf(props(networkControllerRef, viewHolderRef, syncInfoSpec, networkSettings, timeProvider, modifierSerializers), name)

  // Internal interface
  // this is only for test to reach the interface
  private[network] object Timers {
    object SyncIsDoneTimer
  }

  private[network] object InternalReceivableMessages {
    case object SetSyncAsDone

  }

}

