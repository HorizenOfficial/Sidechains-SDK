package io.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.SidechainTypes
import io.horizen.account.sc2sc.{AbstractCrossChainMessageProcessor, AccountCrossChainMessage}
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.Sc2scApiErrorResponse.GenericSc2ScApiError
import io.horizen.api.http.Sc2scApiRouteRestScheme.{ReqCreateAccountRedeemMessage, ReqCreateRedeemMessage, RespCreateRedeemMessage}
import io.horizen.api.http.route.SidechainApiRoute
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.json.Views
import io.horizen.sc2sc.Sc2scProver.ReceivableMessages.BuildRedeemMessage
import io.horizen.sc2sc.{CrossChainMessage, CrossChainProtocolVersion, CrossChainRedeemMessage}
import io.horizen.utils.BytesUtils
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.node._
import sparkz.core.settings.RESTApiSettings

import java.time.Duration
import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

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
  override implicit lazy val timeout: Timeout = akka.util.Timeout.create(Duration.ofSeconds(60))

  override val route: Route = pathPrefix("sc2sc") {
    createRedeemMessage ~ createAccountRedeemMessage
  }

  /**
   * Return a redeem message from  a previously posted CrossChainMessage
   */
  def createRedeemMessage: Route = (post & path("createRedeemMessage")) {
    withBasicAuth {
      _ =>
        entity(as[ReqCreateRedeemMessage]) { body =>

        val crossChainMessage = new CrossChainMessage(
          CrossChainProtocolVersion.fromShort(body.message.protocolVersion),
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

  def createAccountRedeemMessage: Route = (post & path("createAccountRedeemMessage")) {
    withBasicAuth {
      _ =>
        entity(as[ReqCreateAccountRedeemMessage]) { body =>

          val crossChainMessage = AccountCrossChainMessage(
            body.message.messageType,
            BytesUtils.fromHexString(body.message.sender),
            BytesUtils.fromHexString(body.message.receiverSidechain),
            BytesUtils.fromHexString(body.message.receiver),
            BytesUtils.fromHexString(body.message.payload)
          )

          val bla = AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount(crossChainMessage, BytesUtils.fromHexString(body.scId))
          val future = sc2scProver ? BuildRedeemMessage(bla)
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
                                                protocolVersion: Short,
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
  private[api] case class ReqCreateAccountRedeemMessage(message: AccountCrossChainMessageEle, scId: String)

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class AccountCrossChainMessageEle(
                                                messageType: Int,
                                                sender: String,
                                                receiverSidechain: String,
                                                receiver: String,
                                                payload: String
                                              ) {
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