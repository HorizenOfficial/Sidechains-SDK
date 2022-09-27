package com.horizen.account.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.BlockBaseRestSchema.{ReqGenerateByEpochAndSlot, RespGenerate}
import com.horizen.SidechainTypes
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.node.{AccountNodeView, NodeAccountHistory, NodeAccountMemoryPool, NodeAccountState}
import com.horizen.api.http.BlockBaseErrorResponse.ErrorBlockNotCreated
import com.horizen.api.http.{ApiResponseUtil, BlockBaseApiRoute}
import com.horizen.api.http.JacksonSupport._
import com.horizen.block.SidechainBlockBase
import com.horizen.consensus.{intToConsensusEpochNumber, intToConsensusSlotNumber}
import com.horizen.forge.AbstractForger.ReceivableMessages.TryForgeNextBlockForEpochAndSlot
import com.horizen.node.NodeWalletBase
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import sparkz.core.settings.RESTApiSettings
import scorex.util.ModifierId
import sparkz.core.serialization.SparkzSerializer

import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


case class AccountBlockApiRoute(
                                  override val settings: RESTApiSettings,
                                  sidechainNodeViewHolderRef: ActorRef,
                                  sidechainBlockActorRef: ActorRef,
                                  companion: SparkzSerializer[SidechainTypes#SCAT],
                                  forgerRef: ActorRef)
                                 (implicit override val context: ActorRefFactory, override val ec: ExecutionContext)
  extends BlockBaseApiRoute[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    NodeAccountHistory,
    NodeAccountState,
    NodeWalletBase,
    NodeAccountMemoryPool,
    AccountNodeView] (settings, forgerRef){

  override val route: Route = pathPrefix("block") {
    findById ~ findLastIds ~ findIdByHeight ~ getBestBlockInfo ~ findBlockInfoById ~ startForging ~ stopForging ~ generateBlockForEpochNumberAndSlot ~ getForgingInfo
  }

  def generateBlockForEpochNumberAndSlot: Route = (post & path("generate")) {
    entity(as[ReqGenerateByEpochAndSlot]) { body =>
      // TODO FOR MERGE checkit and possibly move to base class

      val forcedTx: Iterable[SidechainTypes#SCAT] = body.transactionsBytes
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


object AccountBlockRestSchema {

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqFeePayments(blockId: String) {
    require(blockId.length == SidechainBlockBase.BlockIdHexStringLength, s"Invalid id $blockId. Id length must be 64")
  }

}
