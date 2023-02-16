package com.horizen.api.http

import java.time.Duration
import java.util.{Optional => JOptional}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.Sc2scApiRouteRestScheme.{ReqCreateRedeemMessage, RespCreateRedeemMessage}
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.chain.SidechainFeePaymentsInfo
import com.horizen.node._
import com.horizen.sc2sc.{CrossChainMessage, CrossChainMessageImpl, CrossChainProtocolVersion, CrossChainRedeemMessage, Sc2ScProverRef}
import com.horizen.serialization.Views
import sparkz.core.settings.RESTApiSettings

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import com.horizen.sc2sc.Sc2scProver.ReceivableMessages.BuildRedeemMessage
import akka.pattern.ask
import com.horizen.api.http.Sc2scApiErrorResponse.GenericSc2ScApiError
import com.horizen.utils.BytesUtils


case class Sc2scApiRoute(override val settings: RESTApiSettings,
                         sidechainNodeViewHolderRef: ActorRef,
                         sc2scProver: ActorRef
                         )
                        (implicit val context: ActorRefFactory, override val ec: ExecutionContext)
  extends SidechainApiRoute[
  SidechainTypes#SCBT,
  SidechainBlockHeader,
  SidechainBlock,
  SidechainFeePaymentsInfo,
  NodeHistory,
  NodeState,
  NodeWallet,
  NodeMemoryPool,
  SidechainNodeView]
{
  override implicit val tag: ClassTag[SidechainNodeView] = ClassTag[SidechainNodeView](classOf[SidechainNodeView])
  override implicit lazy val timeout = akka.util.Timeout.create(Duration.ofSeconds(60))

  override val route: Route = pathPrefix("sc2sc") {
    createRedeemMessage
  }


  /**
    * Return a redeem message from  a previously posted CrossChainMessage
    */
  def createRedeemMessage: Route = (post & path("createRedeemMessage")) {
    withAuth {
      entity(as[ReqCreateRedeemMessage]) { body =>

        val crossChainMessage = new CrossChainMessageImpl(
          body.message.protocolVersion,
          body.message.messageType,
          BytesUtils.fromHexString(body.message.senderSidechain),
          BytesUtils.fromHexString(body.message.sender),
          BytesUtils.fromHexString(body.message.receiverSidechain),
          BytesUtils.fromHexString(body.message.receiver),
          BytesUtils.fromHexString(body.message.payload)
        )

        val future = sc2scProver ? BuildRedeemMessage(crossChainMessage)
        Await.result(future, timeout.duration).asInstanceOf[Try[CrossChainRedeemMessage]] match {
          case Success(ret) => {
           ApiResponseUtil.toResponse(RespCreateRedeemMessage(ret))
          }
          case Failure(e) =>
            ApiResponseUtil.toResponse(GenericSc2ScApiError("Failed to create redeem message", JOptional.of(e)))
        }
      }
    }
  }
}

object Sc2scApiRouteRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCreateRedeemMessage(message: CrossChainMessageEle)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class CrossChainMessageEle(
                                                protocolVersion: CrossChainProtocolVersion,
                                                messageType: Int,
                                                senderSidechain: String,
                                                sender: String,
                                                receiverSidechain: String,
                                                receiver: String,
                                                payload: String
  ){
    require(senderSidechain != null, "Empty sender Sidechain")
    require(sender != null, "Empty sender address")
    require(receiverSidechain != null, "Empty receiver Sidechain")
    require(receiver != null, "Empty receiver address")
    require(payload != null, "Empty payload ")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreateRedeemMessage(redeemMessage: CrossChainRedeemMessage) extends SuccessResponse
}

object Sc2scApiErrorResponse {
  case class GenericSc2ScApiError(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0700"  //TODO: define proper error codes
  }
}



