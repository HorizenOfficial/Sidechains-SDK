package com.horizen.forge

import java.time.Instant

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.block.{MainchainBlockReference, SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.NoncedBox
import com.horizen.chain.SidechainBlockInfoSerializer
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.params.NetworkParams
import com.horizen.proposition.Proposition
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.SidechainTransaction
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.block.Block
import scorex.util.{ModifierId, ScorexLogging}

import scala.util.{Failure, Success, Try}
import scala.concurrent.{Await, Future}


class Forger(settings: SidechainSettings,
             sidechainNodeViewHolderRef: ActorRef,
             companion: SidechainTransactionsCompanion,
             params: NetworkParams) extends Actor with ScorexLogging {

  import Forger.ReceivableMessages._
  import Forger._
  import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier

  implicit lazy val timeout: Timeout = Timeout(settings.scorexSettings.restApi.timeout)
  override def receive: Receive = {
    case Forger.ReceivableMessages.GetBlockTemplate =>
      val future = sidechainNodeViewHolderRef ? getNextBlockForgingInfo
      val pfi: ForgingInfo = Await.result(future, settings.scorexSettings.restApi.timeout).asInstanceOf[ForgingInfo]
      sender() ! SidechainBlock.create(pfi.parentId, pfi.timestamp, pfi.mainchainBlockRefToInclude, pfi.txsToInclude, pfi.ownerPrivateKey, companion, params).get

    case sbi: TrySubmitBlock =>
      new SidechainBlockSerializer(companion).parseBytesTry(sbi.blockBytes) match {
        case Success(sidechainBlock) =>
          sidechainNodeViewHolderRef ! LocallyGeneratedModifier[SidechainBlock](sidechainBlock)
          sender() ! Success(sidechainBlock.id)
        case Failure(e) =>
          sender() ! Failure(e)
      }

    case Forger.ReceivableMessages.ForgeBlock =>
      val future = sidechainNodeViewHolderRef ? getNextBlockForgingInfo
      val pfi: ForgingInfo = Await.result(future, settings.scorexSettings.restApi.timeout).asInstanceOf[ForgingInfo]

      val blockTry = SidechainBlock.create(pfi.parentId, pfi.timestamp, pfi.mainchainBlockRefToInclude, pfi.txsToInclude, pfi.ownerPrivateKey, companion, params)
      blockTry match {
        case Success(block) =>
          sidechainNodeViewHolderRef ! LocallyGeneratedModifier[SidechainBlock](block)
          sender() ! Success(block.id)
        case Failure(e) =>
          sender() ! Failure(e)
      }

    case a: Any =>
      log.error("Strange input: " + a)
  }
}

object Forger extends ScorexLogging {
  import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

  object ReceivableMessages {

    case object GetBlockTemplate
    case object ForgeBlock
    case class TrySubmitBlock(blockBytes: Array[Byte])
    case class ForgingInfo(parentId: Block.BlockId,
                           timestamp: Block.Timestamp,
                           mainchainBlockRefToInclude: Seq[MainchainBlockReference],
                           txsToInclude: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]],
                           ownerPrivateKey: PrivateKey25519)
  }

  import ReceivableMessages.ForgingInfo

  val getNextBlockForgingInfo: GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, ForgingInfo] = {
    val f: CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool] => ForgingInfo = {
      view: CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool] => {
        val parentId: Block.BlockId = view.history.bestBlockId
        val timestamp: Long = Instant.now.getEpochSecond
        val mainchainBlockRefToInclude: Seq[MainchainBlockReference] = Seq() // TO DO: implement after web client for MC node
        val txsToInclude: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = view.pool.take(SidechainBlock.MAX_MC_BLOCKS_NUMBER) // TO DO: problems with types
          .filter(t => t.isInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
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
      ForgingInfo](f)
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
