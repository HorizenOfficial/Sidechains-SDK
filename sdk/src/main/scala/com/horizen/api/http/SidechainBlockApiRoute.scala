package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.BlockBaseRestSchema.ReqFeePayments
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainBlockRestSchema._
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.box.ZenBox
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import com.horizen.params.NetworkParams
import com.horizen.serialization.Views
import sparkz.core.settings.RESTApiSettings
import sparkz.util.ModifierId
import sparkz.core.serialization.SparkzSerializer

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext


case class SidechainBlockApiRoute(
                                   override val settings: RESTApiSettings,
                                   sidechainNodeViewHolderRef: ActorRef,
                                   sidechainBlockActorRef: ActorRef,
                                   companion: SparkzSerializer[SidechainTypes#SCBT],
                                   forgerRef: ActorRef,
                                   params: NetworkParams)
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
    SidechainNodeView] (settings, sidechainBlockActorRef, companion, forgerRef, params){

  override val route: Route = pathPrefix("block") {
    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ getFeePayments ~ findBlockInfoById ~ startForging ~
      stopForging ~ generateBlockForEpochNumberAndSlot ~ getForgingInfo ~ getCurrentHeight
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
}

object SidechainBlockRestSchema {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespFeePayments(feePayments: Seq[ZenBox]) extends SuccessResponse
}
