package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainBlockRestSchema._
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.box.ZenBox
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import com.horizen.serialization.Views
import scorex.core.settings.RESTApiSettings

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext


case class SidechainBlockApiRoute(override val settings: RESTApiSettings,
                                  sidechainNodeViewHolderRef: ActorRef,
                                  sidechainBlockActorRef: ActorRef, forgerRef: ActorRef)(implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends BlockBaseApiRoute[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock,
    SidechainFeePaymentsInfo,
    NodeHistory,
    NodeState,
    NodeWallet,
    NodeMemoryPool,
    SidechainNodeView] (settings, sidechainBlockActorRef: ActorRef, forgerRef: ActorRef) {

  override val route: Route = pathPrefix("block") {
    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ getFeePayments ~ startForging ~ stopForging ~ generateBlockForEpochNumberAndSlot ~ getForgingInfo
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
  private[api] case class ReqFeePayments(blockId: String) {
    require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespFeePayments(feePayments: Seq[ZenBox]) extends SuccessResponse
}

object SidechainBlockErrorResponse {

}