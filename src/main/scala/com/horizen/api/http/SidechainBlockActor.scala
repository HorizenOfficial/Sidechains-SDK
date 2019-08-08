package com.horizen.api.http

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.{SidechainHistory, SidechainSyncInfo}
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.block.SidechainBlock
import com.horizen.forge.Forger.ReceivableMessages.{StartSidechainBlockForging, TrySubmitBlock}
import scorex.core.PersistentNodeViewModifier
import scorex.core.consensus.HistoryReader
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, SemanticallyFailedModification, SyntacticallyFailedModification}
import scorex.util.{ModifierId, ScorexLogging}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

// Implementation not completed
class SidechainBlockActor[PMOD <: PersistentNodeViewModifier, SI <: SidechainSyncInfo, HR <: SidechainHistory : ClassTag]
      (sidechainNodeViewHolderRef : ActorRef, sidechainForgerRef: ActorRef)(implicit ec : ExecutionContext)
  extends Actor with ScorexLogging {

  private var promise: Option[Promise[List[String]]] = None //Set to none when the actor is not mining, to avoid false triggers
  private var generatedBlocks: List[String] = List()
  implicit lazy val timeout: Timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))

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

  protected def tryToSubmitBlock: Receive = {
    case SubmitSidechainBlock(blockBytes: Array[Byte]) =>
      // TO DO: send to Forger, retrieve result, wait for notification from node, return response
      val future = sidechainForgerRef ? TrySubmitBlock(blockBytes)
      Await.result(future, FiniteDuration(1, TimeUnit.SECONDS)).asInstanceOf[Try[ModifierId]] match { // to do: process await exceptions, use "global" timeout
        case Success(id) =>
          // wait for block processed result
          sender() ! Success(id)
        case Failure(ex) =>
          sender() ! Failure(ex)

      }
  }

  override def receive: Receive = {
    generateSidechainBlocks orElse sidechainViewHolderEvents orElse tryToSubmitBlock orElse
      {
        case a : Any => log.error("Strange input: " + a)
      }
  }
}

object SidechainBlockActor {

  object ReceivableMessages{

    case class GenerateSidechainBlocks(blockCount: Int)
    case class SubmitSidechainBlock(blockBytes: Array[Byte])
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