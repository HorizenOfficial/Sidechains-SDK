package com.horizen.api.http

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.{SidechainHistory, SidechainSyncInfo}
import com.horizen.api.http.SidechainBlockActor.ReceivableMessages.{GenerateSidechainBlocks, SubmitSidechainBlock}
import com.horizen.block.SidechainBlock
import com.horizen.forge.Forger.ReceivableMessages.{ForgeBlock, TrySubmitBlock}
import scorex.core.PersistentNodeViewModifier
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.{ChangedHistory, SemanticallyFailedModification, SyntacticallyFailedModification}
import scorex.util.{ModifierId, ScorexLogging}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


class SidechainBlockActor[PMOD <: PersistentNodeViewModifier, SI <: SidechainSyncInfo, HR <: SidechainHistory : ClassTag]
      (sidechainNodeViewHolderRef : ActorRef, sidechainForgerRef: ActorRef)(implicit ec : ExecutionContext)
  extends Actor with ScorexLogging {

  private var promiseForSeqMap: Map[ModifierId, Promise[Try[Seq[ModifierId]]]] = Map()
  private var promiseMap: Map[ModifierId, Promise[Try[ModifierId]]] = Map()
  private var generatedSequences: Map[ModifierId, Seq[ModifierId]] = Map()
  private var generatedBlocks: Set[ModifierId] = Set()

  implicit lazy val timeout: Timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[SyntacticallyFailedModification[PMOD]])
    context.system.eventStream.subscribe(self, classOf[ChangedHistory[HR]])
  }

  protected def sidechainViewHolderEvents : Receive = {
    case SemanticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      if(generatedBlocks.contains(sidechainBlock.id)) {
        promiseMap.get(sidechainBlock.id) match {
          case Some(p) => p.success(Failure(throwable))
          case _ =>
        }
        generatedBlocks -= sidechainBlock.id
        promiseMap -= sidechainBlock.id
        generatedSequences -= sidechainBlock.id
      }

    case SyntacticallyFailedModification(sidechainBlock: SidechainBlock, throwable) =>
      if(generatedBlocks.contains(sidechainBlock.id)) {
        promiseMap.get(sidechainBlock.id) match {
          case Some(p) => p.success(Failure(throwable))
          case _ =>
        }
        generatedBlocks -= sidechainBlock.id
        promiseMap -= sidechainBlock.id
        generatedSequences -= sidechainBlock.id
      }

    case ChangedHistory(history: SidechainHistory) =>
      val expectedBlocks = generatedBlocks.toSeq
      for(id <- expectedBlocks) {
        if(history.contains(id)) {
          promiseMap.get(id) match {
            case Some(p) => p.success(Success(id))
            case _ =>
          }
          promiseForSeqMap.get(id) match {
            case Some(p) => p.success(Success(generatedSequences.get(id).get))
            case _ =>
          }
          generatedBlocks -= id
          promiseMap -= id
          generatedSequences -= id
        }
      }

  }

  protected def generateSidechainBlocks : Receive = {
    case GenerateSidechainBlocks(blockCount) =>
      var generatedIds: Seq[ModifierId] = Seq()
      for(i <- 1 to blockCount) {
        val future = sidechainForgerRef ? ForgeBlock
        Await.result(future, FiniteDuration(500, TimeUnit.MILLISECONDS)).asInstanceOf[Try[ModifierId]] match { // to do: process await exceptions, use "global" timeout
          case Success(id) =>
            generatedIds = id +: generatedIds
            if(i == blockCount) {
              generatedBlocks += id
              val prom = Promise[Try[Seq[ModifierId]]]()
              generatedSequences += (id -> generatedIds)
              promiseForSeqMap += (id -> prom)
              sender() ! prom.future
            }

          case Failure(ex) =>
            sender() ! Failure(ex)
        }
      }
  }

  protected def tryToSubmitBlock: Receive = {
    case SubmitSidechainBlock(blockBytes: Array[Byte]) =>
      val future = sidechainForgerRef ? TrySubmitBlock(blockBytes)
      Await.result(future, FiniteDuration(1, TimeUnit.SECONDS)).asInstanceOf[Try[ModifierId]] match { // to do: process await exceptions, use "global" timeout
        case Success(id) =>
          generatedBlocks += id
          val prom = Promise[Try[ModifierId]]()
          promiseMap += (id -> prom)
          sender() ! prom.future

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