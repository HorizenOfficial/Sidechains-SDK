package com.horizen.api.http

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.{SidechainHistory, SidechainSyncInfo}
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.GenerateSidechainBlocks
import com.horizen.block.SidechainBlock
import com.horizen.forge.SidechainBlockForger.ReceivableMessages.StartSidechainBlockForging
import scorex.core.PersistentNodeViewModifier
import scorex.core.consensus.HistoryReader
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, SemanticallyFailedModification, SyntacticallyFailedModification}
import scorex.util.ScorexLogging

import scala.concurrent.{ExecutionContext, Promise}
import scala.reflect.ClassTag

// Implementation not completed
class SidechainBlockActor[PMOD <: PersistentNodeViewModifier, SI <: SidechainSyncInfo, HR <: SidechainHistory : ClassTag]
      (sidechainNodeViewHolderRef : ActorRef, sidechainForgerRef: ActorRef)(implicit ec : ExecutionContext)
  extends Actor with ScorexLogging {

  private var promise: Option[Promise[List[String]]] = None //Set to none when the actor is not mining, to avoid false triggers
  private var generatedBlocks: List[String] = List()

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[SyntacticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[ChangedHistory[HR]])
  }

  protected def sidechainViewHolderEvents : Receive = {
    case SemanticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>

    case SyntacticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>

    case ChangedHistory(history: SidechainHistory) =>

  }

  protected def generateSidechainBlocks : Receive = {
    case GenerateSidechainBlocks(blockCount) =>
      generatedBlocks = List()
      promise = Option(Promise[List[String]]) //Set up promise which will contain the generated block ids
      sender() ! promise.get.future //Send future to caller
      sidechainForgerRef ! StartSidechainBlockForging //Invoke start forging for the first time
  }

  override def receive: Receive = {
    generateSidechainBlocks orElse sidechainViewHolderEvents orElse
      {
        case a : Any => log.error("Strange input: " + a)
      }
  }
}

object SidechainBlockActor {

  object ReceivableMessages{

    case class GenerateSidechainBlocks(blockCount: Int)
  }
}

object SidechainBlockActorRef{
  def props(sidechainNodeViewHolderRef: ActorRef, sidechainForgerRef: ActorRef)
           (implicit ec: ExecutionContext): Props =
    Props(new SidechainBlockActor(sidechainNodeViewHolderRef, sidechainForgerRef))

  def apply(sidechainNodeViewHolderRef: ActorRef, sidechainForgerRef: ActorRef)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(sidechainNodeViewHolderRef, sidechainForgerRef))
}