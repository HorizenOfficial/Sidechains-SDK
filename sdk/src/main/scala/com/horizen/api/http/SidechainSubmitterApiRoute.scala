package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.SidechainDebugErrorResponse.{ErrorRetrievingCertGenerationState, ErrorRetrievingCertSignerState, ErrorRetrievingCertSubmitterState}
import com.horizen.api.http.SidechainDebugRestScheme.{RespCertGenerationState, RespCertSignerState, RespCertSubmitterState, RespSubmitterOk}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.ReceivableMessages._
import com.horizen.serialization.Views
import scorex.core.api.http.{ApiDirectives, ApiRoute}
import scorex.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

case class SidechainSubmitterApiRoute(override val settings: RESTApiSettings, certSubmitterRef: ActorRef, sidechainNodeViewHolderRef: ActorRef)
                                     (implicit val context: ActorRefFactory)
  extends ApiRoute
  with ApiDirectives
{
  override val route: Route = pathPrefix("submitter") {
    isCertGenerationActive ~ isCertificateSubmitterEnabled ~ enableCertificateSubmitter ~ disableCertificateSubmitter ~
      isCertificateSignerEnabled ~ enableCertificateSigner ~ disableCertificateSigner
  }

  def isCertGenerationActive: Route = (post & path("isCertGenerationActive")) {
    Try {
      Await.result(certSubmitterRef ? GetCertificateGenerationState, timeout.duration).asInstanceOf[Boolean]
    } match {
      case Success(res) =>
        ApiResponseUtil.toResponse(RespCertGenerationState(res))
      case Failure(e) =>
        log.error("Unable to retrieve certificate generation state.")
        ApiResponseUtil.toResponse(ErrorRetrievingCertGenerationState("Unable to retrieve certificate generation state.", JOptional.of(e)))
    }
  }

  def isCertificateSubmitterEnabled: Route = (post & path("isCertificateSubmitterEnabled")) {
    Try {
      Await.result(certSubmitterRef ? IsSubmitterEnabled, timeout.duration).asInstanceOf[Boolean]
    } match {
      case Success(res) =>
        ApiResponseUtil.toResponse(RespCertSubmitterState(res))
      case Failure(e) =>
        log.error("Unable to retrieve certificate submitter state.")
        ApiResponseUtil.toResponse(ErrorRetrievingCertSubmitterState("Unable to retrieve certificate submitter state.", JOptional.of(e)))
    }
  }

  def enableCertificateSubmitter: Route = (post & path("enableCertificateSubmitter")) {
    certSubmitterRef ! EnableSubmitter
    ApiResponseUtil.toResponse(RespSubmitterOk)
  }

  def disableCertificateSubmitter: Route = (post & path("disableCertificateSubmitter")) {
    certSubmitterRef ! DisableSubmitter
    ApiResponseUtil.toResponse(RespSubmitterOk)
  }

  def isCertificateSignerEnabled: Route = (post & path("isCertificateSignerEnabled")) {
    Try {
      Await.result(certSubmitterRef ? IsCertificateSigningEnabled, timeout.duration).asInstanceOf[Boolean]
    } match {
      case Success(res) =>
        ApiResponseUtil.toResponse(RespCertSignerState(res))
      case Failure(e) =>
        log.error("Unable to retrieve certificate submitter state.")
        ApiResponseUtil.toResponse(ErrorRetrievingCertSignerState("Unable to retrieve certificate submitter state.", JOptional.of(e)))
    }
  }

  def enableCertificateSigner: Route = (post & path("enableCertificateSigner")) {
    certSubmitterRef ! EnableCertificateSigner
    ApiResponseUtil.toResponse(RespSubmitterOk)
  }

  def disableCertificateSigner: Route = (post & path("disableCertificateSigner")) {
    certSubmitterRef ! DisableCertificateSigner
    ApiResponseUtil.toResponse(RespSubmitterOk)
  }
}

object SidechainDebugRestScheme {
  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCertGenerationState(state: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCertSubmitterState(enabled: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespCertSignerState(enabled: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] object RespSubmitterOk extends SuccessResponse
}

object SidechainDebugErrorResponse {
  case class ErrorRetrievingCertGenerationState(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0601"
  }

  case class ErrorRetrievingCertSubmitterState(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0602"
  }

  case class ErrorRetrievingCertSignerState(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0603"
  }
}
