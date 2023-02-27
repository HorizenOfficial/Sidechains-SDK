package com.horizen.utxo.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.SidechainTypes
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.route.SidechainApiRoute
import com.horizen.api.http.{ApiResponseUtil, ErrorResponse, SuccessResponse}
import com.horizen.params.NetworkParams
import com.horizen.json.Views
import com.horizen.utils.BytesUtils
import com.horizen.utxo.api.http.SidechainCswErrorResponse._
import com.horizen.utxo.api.http.SidechainCswRestScheme._
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.csw.CswManager.ReceivableMessages.{GenerateCswProof, GetBoxNullifier, GetCswBoxIds, GetCswInfo}
import com.horizen.utxo.csw.CswManager.Responses.{CswInfo, GenerateCswProofStatus, InvalidAddress, NoProofData, ProofCreationFinished, ProofGenerationInProcess, ProofGenerationStarted, SidechainIsAlive}
import com.horizen.utxo.node._
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


abstract class SidechainCswApiRoute (override val settings: RESTApiSettings,
                                                           val sidechainNodeViewHolderRef: ActorRef,
                                                           val params: NetworkParams)
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


  /**
   * Return ceasing status of the Sidechain
   */
  def hasCeased: Route = (post & path("hasCeased")) {

    Try {
      applyOnNodeView {
        sidechainNodeView =>
          val sidechainState = sidechainNodeView.getNodeState
          ApiResponseUtil.toResponse(RespCswHasCeasedState(sidechainState.hasCeased))
      }
    } match {
      case Success(res) => res
      case Failure(e) =>
        log.error("Unable to retrieve ceasing status of the Sidechain.", e)
        ApiResponseUtil.toResponse(ErrorRetrievingCeasingState("Unable to retrieve ceasing status of the Sidechain.", JOptional.of(e)))
    }
  }

  /**
   * Return if CSW is enabled in the Sidechain
   */
  def isCeasedSidechainWithdrawalEnabled: Route = (post & path("isCSWEnabled")) {
     ApiResponseUtil.toResponse(RespCswIsEnabled(params.isCSWEnabled))
  }
}


object SidechainCswApiRoute {

  def apply(settings: RESTApiSettings,
            sidechainNodeViewHolderRef: ActorRef,
            cswManager: Option[ActorRef],
            params: NetworkParams)
           (implicit context: ActorRefFactory, ec: ExecutionContext): SidechainCswApiRoute = {
    require(params != null, "Network parameters must not be NULL")
   if (params.isCSWEnabled)
      SidechainCswApiRouteCSWEnabled(settings, sidechainNodeViewHolderRef, params, cswManager.get)
    else
      SidechainCswApiRouteCSWDisabled(settings, sidechainNodeViewHolderRef, params)
  }

  private[SidechainCswApiRoute] case class SidechainCswApiRouteCSWEnabled(override val settings: RESTApiSettings,
                                                                          override val sidechainNodeViewHolderRef: ActorRef,
                                                                          override val params: NetworkParams,
                                                                          cswManager: ActorRef)
                                                                         (implicit override val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainCswApiRoute(settings, sidechainNodeViewHolderRef, params) {

    override val route: Route = pathPrefix("csw") {
      hasCeased ~ generateCswProof ~ cswInfo ~ cswBoxIds ~ nullifier ~ isCeasedSidechainWithdrawalEnabled
    }

    /**
     * Create a request for generation of CSW proof for specified box id and sender
     * Then inform about current status of this proof
     */
    def generateCswProof: Route = (post & path("generateCswProof")) {
      withBasicAuth {
        _ => {
          entity(as[ReqGenerationCswState]) { body =>
            Try {
              Await.result(cswManager ? GenerateCswProof(BytesUtils.fromHexString(body.boxId), body.receiverAddress), timeout.duration).asInstanceOf[GenerateCswProofStatus]
            } match {
              case Success(res) =>
                res match {
                  case SidechainIsAlive => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "Sidechain is alive"))
                  case InvalidAddress => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "Invalid MC address"))
                  case NoProofData => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "Sidechain is alive"))
                  case ProofGenerationStarted => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "CSW proof generation is started"))
                  case ProofGenerationInProcess => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "CSW proof generation in process"))
                  case ProofCreationFinished => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "CSW proof generation is finished"))
                }
              case Failure(e) => {
                log.error("Unexpected error during CSW proof generation.")
                ApiResponseUtil.toResponse(ErrorCswGenerationState("Unexpected error during CSW proof generation.", JOptional.of(e)))
              }
            }
          }
        }
        }
    }

    /**
     * return the csw info for given BoxId
     */
    def cswInfo: Route = (post & path("cswInfo")) {
      entity(as[ReqCswInfo]) { body =>
        Try {
          Await.result(cswManager ? GetCswInfo(BytesUtils.fromHexString(body.boxId)), timeout.duration).asInstanceOf[Try[CswInfo]]
        } match {
          case Success(cswInfoTry: Try[CswInfo]) =>
            cswInfoTry match {
              case Success(cswInfo: CswInfo) => ApiResponseUtil.toResponse(RespCswInfo(cswInfo))
              case Failure(e) => {
                log.error(e.getMessage)
                ApiResponseUtil.toResponse(ErrorRetrievingCswInfo(e.getMessage, JOptional.of(e)))
              }
            }
          case Failure(e) => {
            log.error("Unexpected error during retrieving CSW info.")
            ApiResponseUtil.toResponse(ErrorRetrievingCswInfo("Unexpected error during retrieving CSW info.", JOptional.of(e)))
          }
        }
      }
    }

    /**
     * Return the list with all box ids.
     */
    def cswBoxIds: Route = (post & path("cswBoxIds")) {
      Try {
        Await.result(cswManager ? GetCswBoxIds, timeout.duration).asInstanceOf[Seq[Array[Byte]]]
      } match {
        case Success(boxIds: Seq[Array[Byte]]) => {
          val boxIdsStr = boxIds.map(id => BytesUtils.toHexString(id))
          ApiResponseUtil.toResponse(RespCswBoxIds(boxIdsStr))
        }
        case Failure(e) => {
          log.error("Unexpected error during retrieving CSW Box Ids.")
          ApiResponseUtil.toResponse(ErrorRetrievingCswBoxIds("Unexpected error during retrieving CSW Box Ids.", JOptional.of(e)))
        }
      }
    }

    /**
     * Return the nullifier for the given coin box id.
     */
    def nullifier: Route = (post & path("nullifier")) {
      entity(as[ReqNullifier]) { body =>
        Try {
          Await.result(cswManager ? GetBoxNullifier(BytesUtils.fromHexString(body.boxId)), timeout.duration).asInstanceOf[Try[Array[Byte]]]
        } match {
          case Success(nullifierTry: Try[Array[Byte]]) => {
            nullifierTry match {
              case Success(nullifier: Array[Byte]) => ApiResponseUtil.toResponse(RespNullifier(BytesUtils.toHexString(nullifier)))
              case Failure(e) =>
                log.error(e.getMessage)
                ApiResponseUtil.toResponse(ErrorRetrievingNullifier(e.getMessage, JOptional.of(e)))
            }
          }
          case Failure(e) => {
            log.error("Unexpected error during retrieving the nullifier.")
            ApiResponseUtil.toResponse(ErrorRetrievingNullifier("Unexpected error during retrieving the nullifier.", JOptional.of(e)))
          }
        }
      }
    }
  }

  private[SidechainCswApiRoute] case class SidechainCswApiRouteCSWDisabled(override val settings: RESTApiSettings,
                                                                           override val sidechainNodeViewHolderRef: ActorRef,
                                                                           override val params: NetworkParams)
                                                                          (implicit override val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainCswApiRoute(settings, sidechainNodeViewHolderRef, params) {
    override val route: Route = pathPrefix("csw") {
      hasCeased ~ isCeasedSidechainWithdrawalEnabled ~ notImplemented
    }

    /**
     * Default implementation for all methods not supported when CSW is disabled.
     */
    def notImplemented: Route = (post & path("cswBoxIds" | "generateCswProof" | "cswInfo" | "nullifier")) {
      ApiResponseUtil.toResponse(ErrorCSWNotEnabled())
    }

  }
}


  object SidechainCswRestScheme {
  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespCswHasCeasedState(state: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespCswIsEnabled(cswEnabled: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqGenerationCswState(boxId: String, receiverAddress: String) {
    require(boxId.length == 64, s"Invalid id $boxId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespGenerationCswState(state: String, description: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqCswInfo(boxId: String) {
    require(boxId.length == 64, s"Invalid id $boxId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespCswInfo(cswInfo: CswInfo) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespCswBoxIds(cswBoxIds: Seq[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqNullifier(boxId: String) {
    require(boxId.length == 64, s"Invalid id $boxId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespNullifier(nullifier: String) extends SuccessResponse
}

object SidechainCswErrorResponse {
  case class ErrorRetrievingCeasingState(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0701"
  }

  case class ErrorCswGenerationState(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0702"
  }

  case class ErrorRetrievingCswInfo(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0703"
  }

  case class ErrorRetrievingCswBoxIds(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0704"
  }

  case class ErrorRetrievingNullifier(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0705"
  }

  case class ErrorCSWNotEnabled() extends ErrorResponse {
    override val description: String = "Operation not supported because Ceased Sidechain Withdrawal is disabled."
    override val code: String = "0707"
    override val exception: JOptional[Throwable] = JOptional.empty()
  }

}