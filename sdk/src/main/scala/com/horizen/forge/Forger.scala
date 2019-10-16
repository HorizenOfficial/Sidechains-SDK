package com.horizen.forge

import java.time.Instant

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.NoncedBox
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.SidechainTransaction
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.block.Block
import scorex.util.ScorexLogging

import scala.util.{Failure, Success, Try}
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration


class Forger(settings: SidechainSettings,
             sidechainNodeViewHolderRef: ActorRef,
             companion: SidechainTransactionsCompanion,
             params: NetworkParams) extends Actor with ScorexLogging {

  import Forger.ReceivableMessages._
  import Forger._
  import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout / 4
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  protected def getNodeView: View = {
    val future = sidechainNodeViewHolderRef ? getCurrentNodeView
    Await.result(future, timeoutDuration).asInstanceOf[View]
  }

  protected def tryGetBlockTemplate: Receive = {
    case Forger.ReceivableMessages.TryGetBlockTemplate =>
      val future = sidechainNodeViewHolderRef ? getNextBlockForgingInfo
      try {
        val tryForgingInfo: Try[ForgingInfo] = Await.result(future, timeoutDuration).asInstanceOf[Try[ForgingInfo]]
        tryForgingInfo match {
          case Success(pfi) =>
            sender() ! SidechainBlock.create(pfi.parentId, pfi.timestamp, pfi.mainchainBlockRefToInclude, pfi.txsToInclude, pfi.ownerPrivateKey, companion, params)
          case Failure(e) =>
            sender() ! Failure(new Exception(s"Unable to collect information for block template creation: ${e.getMessage}"))
        }
      } catch {
        case _ => sender ! Failure(new Exception(s"Unable to collect information for block template creation: Timeout exception"))
      }
  }

  protected def trySubmitBlock: Receive = {
    case sbi: TrySubmitBlock =>
      new SidechainBlockSerializer(companion).parseBytesTry(sbi.blockBytes) match {
        case Success(sidechainBlock) =>
          if(!getNodeView.history.contains(sidechainBlock.id)) {
            sidechainNodeViewHolderRef ! LocallyGeneratedModifier[SidechainBlock](sidechainBlock)
            sender() ! Success(sidechainBlock.id)
          }
          else
            sender() ! Failure(new Exception("Block is already applied."))
        case Failure(e) =>
          sender() ! Failure(e)
      }
  }

  protected def tryForgeNextBlock: Receive = {
    case Forger.ReceivableMessages.TryForgeNextBlock =>
      val future = sidechainNodeViewHolderRef ? getNextBlockForgingInfo
      try {
        val tryForgingInfo: Try[ForgingInfo] = Await.result(future, timeoutDuration).asInstanceOf[Try[ForgingInfo]]
        tryForgingInfo match {
          case Success(pfi) =>
            SidechainBlock.create(pfi.parentId, pfi.timestamp, pfi.mainchainBlockRefToInclude, pfi.txsToInclude, pfi.ownerPrivateKey, companion, params) match {
              case Success(block) =>
                sidechainNodeViewHolderRef ! LocallyGeneratedModifier[SidechainBlock](block)
                sender() ! Success(block.id)
              case Failure(e) =>
                sender() ! Failure(e)
            }
          case Failure(e) =>
            sender() ! Failure(new Exception(s"Unable to collect information for block template creation: ${e.getMessage}"))
        }
      } catch {
        case e: Exception => sender ! Failure(new Exception(s"Unable to collect information for block template creation: Timeout exception"))
      }
  }

  override def receive: Receive = {
    tryGetBlockTemplate orElse trySubmitBlock orElse tryForgeNextBlock orElse {
      case message: Any => log.error("Forger received strange message: " + message)
    }
  }
}

object Forger extends ScorexLogging {
  import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

  object ReceivableMessages {

    case object TryGetBlockTemplate
    case object TryForgeNextBlock
    case class TrySubmitBlock(blockBytes: Array[Byte])
    case class ForgingInfo(parentId: Block.BlockId,
                           timestamp: Block.Timestamp,
                           mainchainBlockRefToInclude: Seq[MainchainBlockReference],
                           txsToInclude: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
                           ownerPrivateKey: PrivateKey25519)
  }

  import ReceivableMessages.ForgingInfo

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  val getCurrentNodeView: GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, View] = {
    def f(v: View): View = v
    GetDataFromCurrentView[SidechainHistory,
      SidechainState,
      SidechainWallet,
      SidechainMemoryPool,
      Forger.View](f)
  }

  val getNextBlockForgingInfo: GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, Try[ForgingInfo]] = {
    val f: View => Try[ForgingInfo] = {
      view: View => Try {
        val parentId: Block.BlockId = view.history.bestBlockId
        val timestamp: Long = Instant.now.getEpochSecond
        val mainchainBlockRefToInclude: Seq[MainchainBlockReference] = Seq() // TO DO: implement after web client for MC node
        val txsToInclude: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] =
          view.pool.take(SidechainBlock.MAX_SIDECHAIN_TXS_NUMBER) // TO DO: problems with types
          .withFilter(t => t.isInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
          .map(t => t.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
          .toSeq
        val ownerPrivateKey:PrivateKey25519 = view.vault.secrets().headOption match {
          case Some(secret) =>
            secret.asInstanceOf[PrivateKey25519] // TO DO: problems with types
          case None =>
            // TO DO: do we need to generate new secret here or return error?
            val secret = PrivateKey25519Creator.getInstance().generateSecret(view.vault.secrets().size.toString.getBytes())
            view.vault.addSecret(secret)
            secret
        }
        ForgingInfo(parentId, timestamp, mainchainBlockRefToInclude, txsToInclude, ownerPrivateKey)
      }
    }
    GetDataFromCurrentView[SidechainHistory,
      SidechainState,
      SidechainWallet,
      SidechainMemoryPool,
      Try[ForgingInfo]](f)
  }
}

object ForgerRef {
  def props(settings: SidechainSettings, viewHolderRef: ActorRef,
            companion: SidechainTransactionsCompanion, params: NetworkParams): Props = Props(new Forger(settings, viewHolderRef, companion, params))

  def apply(settings: SidechainSettings, viewHolderRef: ActorRef, companion: SidechainTransactionsCompanion, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, companion, params))

  def apply(name: String, settings: SidechainSettings, viewHolderRef: ActorRef, companion: SidechainTransactionsCompanion, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, viewHolderRef, companion, params), name)
}
