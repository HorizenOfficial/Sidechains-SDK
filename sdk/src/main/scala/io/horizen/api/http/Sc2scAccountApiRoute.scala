package io.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.SidechainTypes
import io.horizen.account.sc2sc.{AbstractCrossChainMessageProcessor, AccountCrossChainMessage, AccountCrossChainRedeemMessage}
import io.horizen.account.storage.AccountStateMetadataStorage
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.Sc2ScAccountApiErrorResponse.GenericSc2ScAccountApiError
import io.horizen.api.http.Sc2ScAccountApiRouteRestScheme.{ReqCreateAccountRedeemMessage, RespCreateRedeemMessage}
import io.horizen.api.http.route.SidechainApiRoute
import io.horizen.fork.{ForkManager, Sc2ScFork}
import io.horizen.json.Views
import io.horizen.sc2sc.{CrossChainRedeemMessage, Sc2ScUtils}
import io.horizen.sc2sc.Sc2scProver.ReceivableMessages.BuildRedeemMessage
import io.horizen.utils.BytesUtils
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.node._
import sparkz.core.settings.RESTApiSettings

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

case class Sc2scAccountApiRoute(override val settings: RESTApiSettings,
                                sidechainNodeViewHolderRef: ActorRef,
                                sc2scProver: ActorRef,
                                stateMetadataStorage: AccountStateMetadataStorage
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
    SidechainNodeView] {
  override implicit val tag: ClassTag[SidechainNodeView] = ClassTag[SidechainNodeView](classOf[SidechainNodeView])
  override implicit lazy val timeout: Timeout = akka.util.Timeout.create(Duration.ofSeconds(60))

  override val route: Route = pathPrefix("sc2sc") {
    createAccountRedeemMessage
  }

  def createAccountRedeemMessage: Route = (post & path("createAccountRedeemMessage")) {
    withBasicAuth {
      _ =>
        entity(as[ReqCreateAccountRedeemMessage]) { body =>
          if (Sc2ScUtils.isActive(ForkManager.getOptionalSidechainFork[Sc2ScFork](stateMetadataStorage.getConsensusEpochNumber.getOrElse(0)))) {
            val accountCcMsg = AccountCrossChainMessage(
              body.message.messageType,
              BytesUtils.fromHexString(body.message.sender),
              BytesUtils.fromHexString(body.message.receiverSidechain),
              BytesUtils.fromHexString(body.message.receiver),
              body.message.payload.getBytes(StandardCharsets.UTF_8)
            )

            val crossChainMessage = AbstractCrossChainMessageProcessor.buildCrossChainMessageFromAccount(accountCcMsg, BytesUtils.fromHexString(body.scId))
            val future = sc2scProver ? BuildRedeemMessage(crossChainMessage)
            Await.result(future, timeout.duration).asInstanceOf[Try[CrossChainRedeemMessage]] match {
              case Success(ret) =>
                ApiResponseUtil.toResponse(RespCreateRedeemMessage(ret))
              case Failure(e) =>
                ApiResponseUtil.toResponse(GenericSc2ScAccountApiError("Failed to create redeem message", JOptional.of(e)))
            }
          } else {
            ApiResponseUtil.toResponse(GenericSc2ScAccountApiError("Cannot create redeem message if sc2sc feature is not active"))
          }
        }
    }
  }
}

object Sc2ScAccountApiRouteRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCreateRedeemMessage(redeemMessage: CrossChainRedeemMessage) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class CrossChainMessageEle(
                                                protocolVersion: Short,
                                                messageType: Int,
                                                senderSidechain: String,
                                                sender: String,
                                                receiverSidechain: String,
                                                receiver: String,
                                                payload: String
                                              ) {
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
}

object Sc2ScAccountApiErrorResponse {
  case class GenericSc2ScAccountApiError(description: String, exception: JOptional[Throwable] = JOptional.empty()) extends ErrorResponse {
    override val code: String = "0700" //TODO: define proper error codes
  }
}