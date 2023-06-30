package io.horizen.api.http.route

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.fasterxml.jackson.annotation.JsonView
import io.horizen.api.http.JacksonSupport._
import io.horizen.api.http.route.SidechainDebugErrorResponse._
import io.horizen.api.http.route.SidechainDebugRestScheme._
import io.horizen.api.http.{ApiResponseUtil, ErrorResponse, SidechainApiError, SuccessResponse}
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter.ReceivableMessages._
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.cryptolibprovider.CircuitTypes.{CircuitTypes, NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.json.Views
import io.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import io.horizen.params.NetworkParams
import io.horizen.transaction.Transaction
import io.horizen.utils.BytesUtils
import io.horizen.{AbstractState, SidechainNodeViewBase}
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
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, NS, NW, NP]](override val settings: RESTApiSettings, params: NetworkParams, certSubmitterRef: ActorRef, sidechainNodeViewHolderRef: ActorRef, circuitType: CircuitTypes)
                                     (implicit val context: ActorRefFactory, override val ec: ExecutionContext, override val tag: ClassTag[NV])
  extends SidechainApiRoute[TX, H, PM, FPI, NH, NS, NW, NP, NV]
  with ApiDirectives
{

  val submitterPathPrefix = "submitter"

  override val route: Route = pathPrefix(submitterPathPrefix) {
    isCertGenerationActive ~ isCertificateSubmitterEnabled ~ enableCertificateSubmitter ~ disableCertificateSubmitter ~
      isCertificateSignerEnabled ~ enableCertificateSigner ~ disableCertificateSigner ~ getKeyRotationProof ~
      getSigningKeyRotationMessageToSign ~ getMasterKeyRotationMessageToSign ~ getCertifiersKeys
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



  def getSigningKeyRotationMessageToSign: Route = (post & path("getKeyRotationMessageToSignForSigningKey")) {
    retrieveMessageToSign(CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.getMsgToSignForSigningKeyUpdate)
  }

  def getMasterKeyRotationMessageToSign: Route = (post & path("getKeyRotationMessageToSignForMasterKey")) {
    retrieveMessageToSign(CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.getMsgToSignForMasterKeyUpdate)
  }

  private def retrieveMessageToSign(getMessageToSign: (Array[Byte], Int, Array[Byte]) => Array[Byte]) = {
    entity(as[ReqGetKeyRotationMessageToSign]) { body =>
      circuitType match {
        case NaiveThresholdSignatureCircuit =>
          ApiResponseUtil.toResponse(ErrorBadCircuit("The current circuit doesn't support key rotation message to sign!", JOptional.empty()))
        case NaiveThresholdSignatureCircuitWithKeyRotation =>
          val message = getMessageToSign(
            BytesUtils.fromHexString(body.schnorrPublicKey), body.withdrawalEpoch, params.sidechainId)
          ApiResponseUtil.toResponse(RespKeyRotationMessageToSign(message))
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
    } catch {
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
   private[horizen] case class RespCertGenerationState(state: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespCertSubmitterState(enabled: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespCertSignerState(enabled: Boolean) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] object RespSubmitterOk extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqGetKeyRotationMessageToSign(schnorrPublicKey: String, withdrawalEpoch: Int) {
    require(schnorrPublicKey != null && schnorrPublicKey.nonEmpty, "Null key")
    require(withdrawalEpoch >= 0, "Withdrawal epoch is negative")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqKeyRotationProof(withdrawalEpoch: Int,
                                              indexOfKey: Int,
                                              keyType: Int) {
    require(withdrawalEpoch >= 0, "Withdrawal epoch is negative")
    require(indexOfKey >= 0, "Key index is negative")
    require(keyType == 0 || keyType == 1, "Key type can be only 0 for signing and 1 for master key")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class ReqGetCertificateSigners(withdrawalEpoch: Int) {
    require(withdrawalEpoch >= -1, "Withdrawal epoch is smaller than -1")
  }

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespGetCertificateSigners(certifiersKeys: CertifiersKeys, keysRootHash: Array[Byte]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespGetKeyRotationProof(keyRotationProof: Option[KeyRotationProof]) extends SuccessResponse

  @JsonView(Array(classOf[Views.Default]))
   private[horizen] case class RespKeyRotationMessageToSign(keyRotationMessageToSign: Array[Byte]) extends SuccessResponse
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
