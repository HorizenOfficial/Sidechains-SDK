package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.BlockBaseRestSchema.{ReqFeePayments, ReqGenerateByEpochAndSlot, RespGenerate}
import com.horizen.SidechainTypes
import com.horizen.api.http.BlockBaseErrorResponse.ErrorBlockNotCreated
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainBlockRestSchema._
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.box.ZenBox
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.consensus.{intToConsensusEpochNumber, intToConsensusSlotNumber}
import com.horizen.forge.AbstractForger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import sparkz.core.settings.RESTApiSettings
import sparkz.util.ModifierId
import sparkz.core.serialization.SparkzSerializer
import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


case class SidechainBlockApiRoute(
                                   override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef,
                                   sidechainBlockActorRef: ActorRef,
                                   companion: SparkzSerializer[SidechainTypes#SCBT],
                                   forgerRef: ActorRef)
                                 (implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends BlockBaseApiRoute[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] (settings, forgerRef){

  override val route: Route = pathPrefix("block") {

    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ getFeePayments ~ findBlockInfoById ~ startForging ~ stopForging ~ generateBlockForEpochNumberAndSlot ~ getForgingInfo
  }

  /**
   * Return the list of forgers fee payments paid after the given block was applied.
   * Return empty list in case no fee payments were paid.
   */
  def getFeePayments: Route = (post & path("getFeePayments")) {
    entity(as[ReqFeePayments]) { body =>
      applyOnNodeView { sidechainNodeView =>
        val sidechainHistory = sidechainNodeView.getNodeHistory
        val feePayments = sidechainHistory.getFeePaymentsInfo(body.blockId).asScala.map(_.transaction.newBoxes().asScala).getOrElse(Seq())
        ApiResponseUtil.toResponse(RespFeePayments(feePayments))
      }
    }
  }

  def generateBlockForEpochNumberAndSlot: Route = (post & path("generate")) {
    entity(as[ReqGenerateByEpochAndSlot]) { body =>
      val forcedTx: Iterable[SidechainTypes#SCBT] = body.transactionsBytes
        .map(txBytes => companion.parseBytesTry(BytesUtils.fromHexString(txBytes)))
        .flatten(maybeTx => maybeTx.map(Seq(_)).getOrElse(None))

      val future = sidechainBlockActorRef ? TryForgeNextBlockForEpochAndSlot(intToConsensusEpochNumber(body.epochNumber), intToConsensusSlotNumber(body.slotNumber), forcedTx)
      val submitResultFuture = Await.result(future, timeout.duration).asInstanceOf[Future[Try[ModifierId]]]

      Await.result(submitResultFuture, timeout.duration) match {
        case Success(id) =>
          ApiResponseUtil.toResponse(RespGenerate(id.asInstanceOf[String]))
        case Failure(e) =>
          ApiResponseUtil.toResponse(ErrorBlockNotCreated(s"Block was not created: ${e.getMessage}", JOptional.empty()))
      }

    }
  }

}


object SidechainBlockRestSchema {


  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespFeePayments(feePayments: Seq[ZenBox]) extends SuccessResponse

}
