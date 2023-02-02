package com.horizen.api.http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import com.horizen.RemoteKeysManagerSettings
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.client.SecureEnclaveApiClient.{CreateSignatureRequest, CreateSignatureResponse, ListPublicKeysRequest, ListPublicKeysResponse}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.CertificateSignatureInfo
import com.horizen.proof.SchnorrSignatureSerializer
import com.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import com.horizen.utils.BytesUtils
import io.circe.generic.auto._
import io.circe.syntax._
import sparkz.util.SparkzLogging

import scala.concurrent.{ExecutionContext, Future}

class SecureEnclaveApiClient(settings: RemoteKeysManagerSettings)(implicit system: ActorSystem, ec: ExecutionContext) extends SparkzLogging {

  private[client] val http: HttpExt = Http(system)
  private val keySerializer: SchnorrPropositionSerializer = SchnorrPropositionSerializer.getSerializer
  private val signatureSerializer: SchnorrSignatureSerializer = SchnorrSignatureSerializer.getSerializer
  val SCHNORR = "schnorr"

  def isEnabled: Boolean = settings.enabled

  def listPublicKeys(): Future[Seq[SchnorrProposition]] = {
    http.singleRequest(
      Post(settings.address + "/api/v1/listKeys")
        .withEntity(HttpEntity(ListPublicKeysRequest(SCHNORR).asJson.noSpaces))
    )
      .flatMap {
        case response@HttpResponse(StatusCodes.OK, _, _, _) =>
          Unmarshal(response).to[ListPublicKeysResponse].map {
            case ListPublicKeysResponse(Some(publicKeys), _) =>
              publicKeys.map(_.publicKey).map(deserializePublicKey)
            case ListPublicKeysResponse(None, Some(error)) =>
              logger.warn(s"Error getting list of keys from secure enclave: [$error]")
              Seq()
            case _ => Seq()
          }
        case response: HttpResponse =>
          logger.error(s"Secure Enclave returned non-200 response: ${response.status}, ${response.entity}")
          Future(Seq())
      }
      .recover {
        case e: Exception =>
          logger.error(s"Unable to connect to secure enclave: ${e.getMessage}")
          Seq()
      }
  }

  def signWithEnclave(message: Array[Byte], publicKey_index: (SchnorrProposition, Int)): Future[Option[CertificateSignatureInfo]] = {
    http.singleRequest(buildSignMessageRequest(message, publicKey_index._1))
      .flatMap {
        case response@HttpResponse(StatusCodes.OK, _, _, _) =>
          Unmarshal(response).to[CreateSignatureResponse].map {
            case CreateSignatureResponse(Some(signature), _) =>
              Some(CertificateSignatureInfo(
                publicKey_index._2,
                signatureSerializer.parseBytes(BytesUtils.fromHexString(signature))
              ))
            case CreateSignatureResponse(None, Some(error)) =>
              logger.warn(s"Error creating signature: [$error]")
              None
            case _ => None
          }
        case response: HttpResponse =>
          logger.error(s"Secure Enclave returned non-200 response: ${response.status}")
          Future(None)
      }
      .recover {
        case e: Exception =>
          logger.error(s"Unable to connect to secure enclave: ${e.getMessage}")
          None
      }
  }

  private def buildSignMessageRequest(message: Array[Byte], publicKey: SchnorrProposition): HttpRequest = {
    Post(settings.address + "/api/v1/createSignature")
      .withEntity(
        HttpEntity(
          CreateSignatureRequest(BytesUtils.toHexString(message), serializePublicKey(publicKey), SCHNORR).asJson.noSpaces
        )
      )
  }

  private def serializePublicKey(publicKey: SchnorrProposition) =
    BytesUtils.toHexString(keySerializer.toBytes(publicKey))

  private def deserializePublicKey(publicKey: String) =
    keySerializer.parseBytes(BytesUtils.fromHexString(publicKey))
}

object SecureEnclaveApiClient {

  case class CreateSignatureRequest(message: String,
                                    publicKey: String,
                                    `type`: String)

  case class CreateSignatureResponse(signature: Option[String],
                                     error: Option[String])

  case class ListPublicKeysRequest(`type`: String)

  case class PublicKeyResponse(publicKey: String,
                               `type`: String)

  case class ListPublicKeysResponse(keys: Option[Seq[PublicKeyResponse]],
                                    error: Option[String])
}
