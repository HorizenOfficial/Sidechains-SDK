package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.SidechainCswRestScheme.{ReqCswInfo, ReqGenerationCswState, ReqNullifier, RespCswBoxIds, RespCswHasCeasedState, RespCswInfo, RespGenerationCswState, RespNullifier}
import com.horizen.serialization.Views

import java.util.{Optional => JOptional}
import akka.pattern.ask
import com.horizen.api.http.SidechainCswErrorResponse.{ErrorCswGenerationState, ErrorRetrievingCeasingState, ErrorRetrievingCswBoxIds, ErrorRetrievingCswInfo, ErrorRetrievingNullifier}
import com.horizen.csw.CswManager.ReceivableMessages.{GenerateCswProof, GetBoxNullifier, GetCeasedStatus, GetCswBoxIds, GetCswInfo}
import com.horizen.csw.CswManager.Responses.{CswInfo, GenerateCswProofStatus, InvalidAddress, NoProofData, ProofCreationFinished, ProofGenerationInProcess, ProofGenerationStarted, SidechainIsAlive}
import com.horizen.api.http.JacksonSupport._
import com.horizen.utils.BytesUtils
import scorex.core.settings.RESTApiSettings

import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

case class SidechainCswApiRoute(override val settings: RESTApiSettings,
                                sidechainNodeViewHolderRef: ActorRef,
                                cswManager: ActorRef)
                               (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute {

  override val route: Route = pathPrefix("csw") {
    hasCeased ~ generateCswProof ~ cswInfo ~ cswBoxIds ~ nullifier
  }

  /**
   * Return ceasing status of the Sidechain
   */
  def hasCeased: Route = (post & path("hasCeased")) {
    Try {
      Await.result(cswManager ? GetCeasedStatus, timeout.duration).asInstanceOf[Boolean]
    } match {
      case Success(res) =>
        ApiResponseUtil.toResponse(RespCswHasCeasedState(res))
      case Failure(e) => {
        log.error("Unable to retrieve ceasing status of the Sidechain.")
        ApiResponseUtil.toResponse(ErrorRetrievingCeasingState("Unable to retrieve ceasing status of the Sidechain.", JOptional.of(e)))
      }
    }
  }

  /**
   * Create a request for generation of CSW proof for specified box id and sender
   * Then inform about current status of this proof
   */
  def generateCswProof: Route = (post & path("generateCswProof")) {
    entity(as[ReqGenerationCswState]) { body =>
      Try {
        Await.result(cswManager ? GenerateCswProof(BytesUtils.fromHexString(body.boxId), body.senderAddress), timeout.duration).asInstanceOf[GenerateCswProofStatus]
      } match {
        case Success(res) =>
          res match {
            case SidechainIsAlive => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "Sidechain is alive"))
            case InvalidAddress  => ApiResponseUtil.toResponse(RespGenerationCswState(res.toString(), "Invalid MC address"))
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

object SidechainCswRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCswHasCeasedState(state: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGenerationCswState(boxId: String, senderAddress: String) {
    require(boxId.length == 64, s"Invalid id $boxId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGenerationCswState(state: String, description: String) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqCswInfo(boxId: String) {
    require(boxId.length == 64, s"Invalid id $boxId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCswInfo(cswInfo: CswInfo) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCswBoxIds(cswBoxIds: Seq[String]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqNullifier(boxId: String) {
    require(boxId.length == 64, s"Invalid id $boxId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespNullifier(nullifier: String) extends SuccessResponse
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
}