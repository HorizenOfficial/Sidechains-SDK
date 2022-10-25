package com.horizen.api.http.client

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.pattern.pipe
import com.horizen.RemoteKeysManagerSettings
import com.horizen.api.http.JacksonSupport._
import com.horizen.api.http.client.SecureEnclaveApiClient.{CreateSignatureRequest, CreateSignatureResponse, SignWithEnclave}
import com.horizen.certificatesubmitter.CertificateSubmitter.CertificateSignatureInfo
import com.horizen.proof.SchnorrSignatureSerializer
import com.horizen.proposition.{SchnorrProposition, SchnorrPropositionSerializer}
import com.horizen.utils.BytesUtils
import io.circe.generic.auto._
import io.circe.syntax._
import scorex.util.ScorexLogging

import scala.concurrent.{ExecutionContext, Future}

class SecureEnclaveApiClient(settings: RemoteKeysManagerSettings)(implicit ec: ExecutionContext) extends Actor with ScorexLogging {

  implicit val system: ActorSystem = context.system
  private[client] val http: HttpExt = Http(system)
  private val keySerializer: SchnorrPropositionSerializer = SchnorrPropositionSerializer.getSerializer
  private val signatureSerializer: SchnorrSignatureSerializer = SchnorrSignatureSerializer.getSerializer

  private def serializePublicKey(publicKey: SchnorrProposition) =
    BytesUtils.toHexString(keySerializer.toBytes(publicKey))

  override def receive: Receive =
    if (settings.enabled) enabled else disabled

  private def buildRequest(message: Array[Byte], publicKey: SchnorrProposition): HttpRequest = {
    Post(settings.address)
      .withEntity(
        HttpEntity(
          CreateSignatureRequest(message, serializePublicKey(publicKey), "schnorr").asJson.noSpaces
        )
      )
  }


  private def enabled: Receive = {
    case SignWithEnclave(message, publicKey_index) =>
      http.singleRequest(buildRequest(message, publicKey_index._1))
        .flatMap {
          case response@HttpResponse(StatusCodes.OK, _, _, _) =>
            Unmarshal(response).to[CreateSignatureResponse].map {
              case CreateSignatureResponse(Some(signature), _) =>
                Some(CertificateSignatureInfo(publicKey_index._2, signatureSerializer.parseBytes(BytesUtils.fromHexString(signature))))
              case CreateSignatureResponse(None, Some(error)) =>
                logger.warn(s"Error from Secure Enclave: [$error]")
                None
              case _ => None
            }
          case _ => Future(None)
        }
        .pipeTo(sender())
  }

  private def disabled: Receive = {
    case SignWithEnclave(_, _) => sender() ! None
  }
}

object SecureEnclaveApiClient {
  case class SignWithEnclave(message: Array[Byte],
                             publicKey_index: (SchnorrProposition, Int))

  case class CreateSignatureRequest(message: Array[Byte],
                                    publicKey: String,
                                    `type`: String)

  case class CreateSignatureResponse(signature: Option[String],
                                     error: Option[String])
}

object SecureEnclaveApiClientRef {
  def props(settings: RemoteKeysManagerSettings)
           (implicit ec: ExecutionContext): Props =
    Props(new SecureEnclaveApiClient(settings))

  def apply(settings: RemoteKeysManagerSettings)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings))
}
