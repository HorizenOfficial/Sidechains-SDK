package com.horizen.api.http


import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import com.horizen.{AbstractState, SidechainNodeViewBase}
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.SidechainDebugErrorResponse._
import com.horizen.api.http.SidechainDebugRestScheme._
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.ReceivableMessages._
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.cryptolibprovider.utils.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import com.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import com.horizen.proposition.SchnorrProposition
import com.horizen.serialization.Views
import com.horizen.transaction.Transaction
import com.horizen.utils.BytesUtils
import sparkz.core.api.http.ApiDirectives
import sparkz.core.settings.RESTApiSettings

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

case class SidechainSubmitterApiRoute[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: AbstractState[TX, H, PM, NS] with NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](override val settings: RESTApiSettings, certSubmitterRef: ActorRef, sidechainNodeViewHolderRef: ActorRef, circuitType: CircuitTypes)
                                     (implicit val context: ActorRefFactory, override val ec: ExecutionContext, override val tag: ClassTag[NV])
  extends SidechainApiRoute[TX, H, PM, FPI, NH, NS, NW, NP, NV]
  with ApiDirectives
{
  override val route: Route = pathPrefix("submitter") {
    isCertGenerationActive ~ isCertificateSubmitterEnabled ~ enableCertificateSubmitter ~ disableCertificateSubmitter ~
      isCertificateSignerEnabled ~ enableCertificateSigner ~ disableCertificateSigner~ getSchnorrPublicKeyHash ~ getCertifiersKeys ~ getKeyRotationProof
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
    withBasicAuth {
      _ => {
        certSubmitterRef ! EnableSubmitter
        ApiResponseUtil.toResponse(RespSubmitterOk)
      }
    }
  }

  def disableCertificateSubmitter: Route = (post & path("disableCertificateSubmitter")) {
    withBasicAuth {
      _ => {
        certSubmitterRef ! DisableSubmitter
        ApiResponseUtil.toResponse(RespSubmitterOk)
      }
    }
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
    withBasicAuth {
      _ => {
        certSubmitterRef ! EnableCertificateSigner
        ApiResponseUtil.toResponse(RespSubmitterOk)
      }
    }
  }

  def disableCertificateSigner: Route = (post & path("disableCertificateSigner")) {
    withBasicAuth {
      _ => {
        certSubmitterRef ! DisableCertificateSigner
        ApiResponseUtil.toResponse(RespSubmitterOk)
      }
    }
  }

  def getSchnorrPublicKeyHash: Route = (post & path("getSchnorrPublicKeyHash")) {
    entity(as[ReqGetSchnorrPublicKeyHash]) { body =>
      try {
        val schnorrPublicKey: SchnorrProposition = new SchnorrProposition(BytesUtils.fromHexString(body.schnorrPublicKey))
        ApiResponseUtil.toResponse(
          RespHashSchnorrPublicKey(
            BytesUtils.toHexString(schnorrPublicKey.getHash)
          )
        )
      } catch {
        case e: Throwable => SidechainApiError(e)
      }
    }
  }

  def getCertifiersKeys: Route = (post & path("getCertifiersKeys")) {
    try {
      entity(as[ReqGetCertificateSigners]) { body =>
          withView { sidechainNodeView =>
            sidechainNodeView.state.certifiersKeys(body.withdrawalEpoch) match {
              case Some(certifiersKeys) =>
                val keysRootHash = if (certifiersKeys.masterKeys.nonEmpty)
                  CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateKeysRootHash(
                    certifiersKeys.signingKeys.map(_.pubKeyBytes()).toList.asJava,
                    certifiersKeys.masterKeys.map(_.pubKeyBytes()).toList.asJava
                  )
                else
                  Array[Byte]()
                ApiResponseUtil.toResponse(RespGetCertificateSigners(certifiersKeys, keysRootHash))
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
  private[api] case class ReqGetSchnorrPublicKeyHash(schnorrPublicKey: String) {
    require(schnorrPublicKey != null && schnorrPublicKey.nonEmpty, "Null key")
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
  private[api] case class RespGetCertificateSigners(certifiersKeys: CertifiersKeys, keysRootHash: Array[Byte]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespGetKeyRotationProof(keyRotationProof: Option[KeyRotationProof]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class RespHashSchnorrPublicKey(schnorrPublicKeyHash: String) extends SuccessResponse
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
