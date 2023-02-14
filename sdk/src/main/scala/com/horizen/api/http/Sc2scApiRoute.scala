package com.horizen.api.http

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
import com.horizen.sc2sc.{CrossChainMessage, CrossChainRedeemMessage}
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

  override val route: Route = pathPrefix("sc2sc") {
    createRedeemMessage
  }


  /**
    * Return a redeem message from  a previously posted CrossChainMessage
    */
  def createRedeemMessage: Route = (post & path("createRedeemMessage")) {
    withAuth {
      entity(as[ReqCreateRedeemMessage]) { body =>
        val future = sc2scProver ? BuildRedeemMessage(body.message)
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
  private[api] case class ReqCreateRedeemMessage(message: CrossChainMessage)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreateRedeemMessage(message: CrossChainRedeemMessage) extends SuccessResponse
}

object Sc2scApiErrorResponse {
  case class GenericSc2ScApiError(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0700"
  }
}



