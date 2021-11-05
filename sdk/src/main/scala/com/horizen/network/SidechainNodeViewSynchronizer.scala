package com.horizen.network

import akka.actor.{ActorRef, ActorRefFactory, Props, Timers}
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.network.SidechainNodeViewSynchronizer.InternalReceivableMessages.SetSyncAsDone
import com.horizen.network.SidechainNodeViewSynchronizer.ReceivableMessages.GetSyncInfo
import com.horizen.network.SidechainNodeViewSynchronizer.SidechainNodeSyncInfo
import com.horizen.network.SidechainNodeViewSynchronizer.Timers.SyncIsDoneTimer
import com.horizen.validation.{BlockInFutureException, InconsistentDataException}
import scorex.core.consensus.History.{Fork, Nonsense, Unknown, Younger}
import scorex.core.consensus.SyncInfo
import scorex.core.network.NetworkControllerSharedMessages.ReceivableMessages.DataFromPeer
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{SemanticallyFailedModification, SemanticallySuccessfulModifier, SyntacticallyFailedModification, SyntacticallySuccessfulModifier}
import scorex.core.network.{ConnectedPeer, NodeViewSynchronizer}
import scorex.core.serialization.ScorexSerializer
import scorex.core.settings.NetworkSettings
import scorex.core.utils.{NetworkTimeProvider}
import scorex.core.{ModifierTypeId, NodeViewModifier, PersistentNodeViewModifier}
import scorex.util.ModifierId

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, SECONDS}


class SidechainNodeViewSynchronizer(networkControllerRef: ActorRef,
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
  protected val delay: Int = calculateDelayForTimer(12)

  case class OtherNodeSyncStatus[SI <: SyncInfo](remote: ConnectedPeer,
                                                 status: SidechainSyncStatus, // CHANGE THIS
                                                 extension: Seq[(ModifierTypeId, ModifierId)])

  private val onSyntacticallyFailedModifier: Receive = {
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
      val aPeer = thePeerThatHasSent(mod)
      aPeer match {
        case Some(peer) =>
          chainIsOnSync=false
          statusTracker.updateForFailing(peer,SidechainFailedSync(exception,timeProvider.time()))
        case None =>
      }
  }

  private val onSemanticallyFailedModifier: Receive = {
    case SemanticallyFailedModification(mod, exception) =>
      val aPeer = thePeerThatHasSent(mod)
      aPeer match {
        case Some(peer) =>
          chainIsOnSync=false
          statusTracker.updateForFailing(peer,SidechainFailedSync(exception,timeProvider.time()))
        case None =>
      }
  }

  private val onSemanticallySuccessfulModifier: Receive = {
    case SemanticallySuccessfulModifier(pmod) =>
      if(statusTracker.betterNeighbourHeight>statusTracker.myHeight+1){  // we're not still synced
        val aPeer = thePeerThatHasSent(pmod)
        aPeer match {
          case Some(peer) =>
            chainIsOnSync=true
            statusTracker.updateStatusWithLastSyncTime(peer,timeProvider.time())
            statusTracker.updateStatusWithMyHeight(peer)
            restartTimer()
         case None =>

        }
      }
      broadcastModifierInv(pmod)
  }

  private val onSyntacticallySuccessfulModifier: Receive ={
      case SyntacticallySuccessfulModifier(pmod) =>
          deliveryTracker.setBlockHeld(pmod.id)
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
    processGetSyncInfo orElse{
      case a: Any => log.error("Strange input: " + a)
    }
  }

  // Uses SidechainSyncStatus instead of status (ComparisonResult) as parent class does
  override   protected def processSync: Receive = {
    case DataFromPeer(spec, syncInfo: SidechainSyncInfo@unchecked, remote)
      if spec.messageCode == syncInfoSpec.messageCode =>
      historyReaderOpt match {
        case Some(historyReader) =>
          val ext = historyReader.continuationIds(syncInfo, networkSettings.desiredInvObjects)
          val comparison = historyReader.compare(syncInfo)
          if (!(ext.nonEmpty || comparison != Younger))
            log.warn("Extension is empty while comparison is younger")
          self ! OtherNodeSyncStatus(remote, SidechainSyncStatus(comparison,syncInfo.chainHeight,historyReader.storage.height), ext )
        case _ =>
      }
  }

  //view holder is telling other node status
  // Uses SidechainSyncStatus instead of status (ComparisonResult) as parent class does
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
          log.info(s"I'm AHEAD, I'm Sending Extensions : $ext and Status = $status")
          sendExtension(remote, status.historyCompare, ext)
        case _ => // does nothing for `Equal` and `Older`
          log.info("case _   SENDER is equal or older ")
      }
  }


  def thePeerThatHasSent(pmod: PersistentNodeViewModifier): Option[ConnectedPeer] = {
    val peerFromWhichIReceived = deliveryTracker.modHadBeenReceivedFromPeer(pmod.id,pmod.modifierTypeId)
    if(peerFromWhichIReceived.isDefined)
      peerFromWhichIReceived
    else
      None
  }

  protected def restartTimer(): Unit = {
    cancelTimerForSyncIsDone()
    startTimerForSyncIsDone()
  }

  def calculateDelayForTimer(param: Int): Int = {
    param // let's observe after with big numbers
  }

  protected def startTimerForSyncIsDone(): Unit ={
    timers.startSingleTimer(SyncIsDoneTimer, SetSyncAsDone, FiniteDuration(delay, SECONDS))
  }

  protected def cancelTimerForSyncIsDone(): Unit={
       timers.cancel(SyncIsDoneTimer)
       log.info(s"I'm Syncing . . . neighbour Height=${statusTracker.betterNeighbourHeight}, my Height=${statusTracker.myHeight}")
  }

  protected def setSyncAsDone:Receive  ={
    case SetSyncAsDone =>
    chainIsOnSync=false
    log.info("I stop Syncing . . . ")
  }

  protected def processGetSyncInfo: Receive = {
    case GetSyncInfo =>
      val myHeight = historyReaderOpt match {
        case Some(historyReader) =>
          historyReader.storage.height
        case None => -1 // @ASKSasha
      }
      val chainHeight = statusTracker.betterNeighbourHeight
      val status = if (chainIsOnSync) "Synchronizing"
                    else
                      if (chainHeight<0)  "Not yet connected"
                        else if(myHeight==chainHeight || myHeight==chainHeight-1) "Synchronized"
                      else "Not Synchronizing"
      val syncError = ""
      val nodeType = "Standard"

      val percent = if (chainHeight>myHeight+1) (myHeight.toFloat/chainHeight.toFloat*100).toInt
                    else if(chainHeight==myHeight+1) 100
                    else -1
      val response = SidechainNodeSyncInfo(status, chainHeight, percent, myHeight, syncError, nodeType)
      sender() !  response
  }


  override protected def viewHolderEvents: Receive = {
    onSemanticallySuccessfulModifier orElse
    onSemanticallyFailedModifier orElse
    onSyntacticallyFailedModifier orElse
    onSyntacticallySuccessfulModifier orElse
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

  object ReceivableMessages {
    case object GetSyncInfo
  }

  case class SidechainNodeSyncInfo(status: String, blockChainHeight: Long,  syncPercentage: Int,   nodeHeight: Long, error: String, nodeType :String)

}



