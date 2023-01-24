package com.horizen.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainDebugErrorResponse._
import com.horizen.api.http.SidechainDebugRestScheme._
import com.horizen.certificatesubmitter.CertificateSubmitter.ReceivableMessages._
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.cryptolibprovider.utils.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import com.horizen.serialization.Views
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

case class SidechainSubmitterApiRoute(override val settings: RESTApiSettings, certSubmitterRef: ActorRef, sidechainNodeViewHolderRef: ActorRef,  circuitType: CircuitTypes)
                                     (implicit val context: ActorRefFactory, override val ec: ExecutionContext) extends SidechainApiRoute {
  override val route: Route = pathPrefix("submitter") {
    isCertGenerationActive ~ isCertificateSubmitterEnabled ~ enableCertificateSubmitter ~ disableCertificateSubmitter ~
      isCertificateSignerEnabled ~ enableCertificateSigner ~ disableCertificateSigner~ getKeyRotationMessageToSign ~ getCertifiersKeys ~ getKeyRotationProof
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

  def getKeyRotationMessageToSign: Route = (post & path("getKeyRotationMessageToSign")) {
    entity(as[ReqGetKeyRotationMessageToSign]) { body =>
      Try {
        Await.result(certSubmitterRef ? GetKeyRotationMessageToSign(body.schnorrPublicKey, body.keyType,
          body.withdrawalEpoch), timeout.duration).asInstanceOf[String]
      } match {
        case Success(res) =>
          ApiResponseUtil.toResponse(RespKeyRotationMessageToSign(res))
        case Failure(e) =>
          log.error("Unable to get message to sign.")
          ApiResponseUtil.toResponse(ErrorRetrievingCertSignerState("Unable to get message to sign.", JOptional.of(e)))
      }
    }
  }

  def getCertifiersKeys: Route = (post & path("getCertifiersKeys")) {
    try {
      entity(as[ReqGetCertificateSigners]) { body =>
          withView { sidechainNodeView =>
            sidechainNodeView.state.certifiersKeys(body.withdrawalEpoch) match {
              case Some(certifiersKeys) =>
                ApiResponseUtil.toResponse(RespGetCertificateSigners(certifiersKeys))
              case None =>
                ApiResponseUtil.toResponse(ErrorRetrieveCertificateSigners("Can not find certifiers keys.", JOptional.empty()))
            }
          }
      }
    } catch
    {
      case e: Throwable => SidechainApiError(e)
    }
  }

  def getKeyRotationProof: Route = (post & path("getKeyRotationProof")) {
    try {
      entity(as[ReqKeyRotationProof]) { body =>
        withView { sidechainNodeView =>
          circuitType match {
            case NaiveThresholdSignatureCircuit =>
              ApiResponseUtil.toResponse(ErrorBadCircuit("The current circuit doesn't support key rotation proofs!", JOptional.empty()))
            case NaiveThresholdSignatureCircuitWithKeyRotation =>
              ApiResponseUtil.toResponse(RespGetKeyRotationProof(sidechainNodeView.state.keyRotationProof(body.withdrawalEpoch, body.indexOfKey, body.keyType)))
          }
        }
      }
    } catch {
      case e: Throwable => SidechainApiError(e)
    }
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

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetKeyRotationMessageToSign(schnorrPublicKey: String, keyType: Int, withdrawalEpoch: Int) {
    require(schnorrPublicKey != null && schnorrPublicKey.nonEmpty, "Null key")
    require(keyType == 0 || keyType == 1, "Key type can be only 0 for signing and 1 for master key")
    require(withdrawalEpoch >= 0, "Withdrawal epoch is negative")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqKeyRotationProof(withdrawalEpoch: Int,
                                              indexOfKey: Int,
                                              keyType: Int) {
    require(withdrawalEpoch >= 0, "Withdrawal epoch is negative")
    require(indexOfKey >= 0, "Key index is negative")
    require(keyType >= 0, "Key type is negative")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqGetCertificateSigners(withdrawalEpoch: Int) {
    require(withdrawalEpoch >= -1, "Withdrawal epoch is smaller than -1")
  }

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGetCertificateSigners(certifiersKeys: CertifiersKeys) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGetKeyRotationProof(keyRotationProof: Option[KeyRotationProof]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespKeyRotationMessageToSign(keyRotationMessageToSign: String) extends SuccessResponse
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

  case class ErrorRetrieveCertificateSigners(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0604"
  }

  case class ErrorBadCircuit(description: String, exception: JOptional[Throwable]) extends ErrorResponse {
    override val code: String = "0605"
  }
}
