package com.horizen.api.http.client

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.horizen.api.http.client.SecureEnclaveApiClient.SignWithEnclave
import com.horizen.certificatesubmitter.CertificateSubmitter.CertificateSignatureInfo
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.proof.SchnorrSignatureSerializer
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import com.horizen.utils.BytesUtils
import com.horizen.{RemoteKeysManagerSettings, SidechainTypes}
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, verifyNoInteractions, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.Random


@RunWith(classOf[JUnitRunner])
class SecureEnclaveApiClientTest extends AnyWordSpec with Matchers with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes {

  implicit val system: ActorSystem = ActorSystem("test-api-client")
  implicit val timeout: Timeout = Timeout(1.second)
  private val signatureSerializer: SchnorrSignatureSerializer = SchnorrSignatureSerializer.getSerializer


  "Secure Enclave Api should " should {

    "return empty future if it is disabled" in {
      val (apiClient, serverMock) = prepareApiClient(false)
      val key = generateKey().publicImage()
      val result = Await.result(
        apiClient ? SignWithEnclave("test".getBytes, (key, 1)), 1.second
      ).asInstanceOf[Option[CertificateSignatureInfo]]

      assert(result.isEmpty)
      verifyNoInteractions(serverMock)
    }

    "return empty future in case of error" in {
      val (apiClient, serverMock) = prepareApiClient()
      val publicKey = generateKey().publicImage()
      val index = 1
      val response = new ObjectMapper()
        .createObjectNode()
        .put("error", "Do Androids Dream of Electric Sheep?")
        .toString

      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenReturn(Future.successful(
          HttpResponse(status = StatusCodes.OK, entity = response)
        ))

      val result = Await.result(
        apiClient ? SignWithEnclave("test".getBytes, (publicKey, index)), 1.second
      ).asInstanceOf[Option[CertificateSignatureInfo]]

      assert(result.isEmpty)
      verify(serverMock).singleRequest(any(), any(), any(), any())
    }

    "return signature info with memorized index" in {
      val (apiClient, serverMock) = prepareApiClient()
      val message = "test".getBytes
      val privateKey = generateKey()
      val publicKey = privateKey.publicImage()
      val index = 1
      val response = new ObjectMapper()
        .createObjectNode()
        .put("signature", BytesUtils.toHexString(signatureSerializer.toBytes(privateKey.sign(message))))
        .toString

      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenReturn(Future.successful(
          HttpResponse(status = StatusCodes.OK, entity = response)
        ))

      val result = Await.result(
        apiClient ? SignWithEnclave(message, (publicKey, index)), 1.second
      ).asInstanceOf[Option[CertificateSignatureInfo]]

      assert(result.isDefined)
      assert(result.get.pubKeyIndex.equals(index))
      assert(result.get.signature.isValid(publicKey, message))
    }

    "process several requests with errors between them" in {
      val (apiClient, serverMock) = prepareApiClient()
      val message = "test".getBytes
      val privateKey = generateKey()
      val publicKey = privateKey.publicImage()
      val response = new ObjectMapper()
        .createObjectNode()
        .put("signature", BytesUtils.toHexString(signatureSerializer.toBytes(privateKey.sign(message))))
        .toString

      var idx = 0
      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenAnswer(_ => {
          idx += 1
          if (idx % 2 == 1)
            Future.successful(HttpResponse(status = StatusCodes.OK, entity = response))
          else
            Future.successful(HttpResponse(StatusCodes.BadRequest))
        })

      val result = Await.result(
        Future.sequence(Seq(
          apiClient ? SignWithEnclave(message, (publicKey, 1)),
          apiClient ? SignWithEnclave(message, (publicKey, 2)),
          apiClient ? SignWithEnclave(message, (publicKey, 3))
        )), 1.second
      ).asInstanceOf[Seq[Option[CertificateSignatureInfo]]]
        .flatten

      assert(result.size.equals(2))
      assert(result.head.pubKeyIndex.equals(1))
      assert(result.head.signature.isValid(publicKey, message))
      assert(result.last.pubKeyIndex.equals(3))
      assert(result.last.signature.isValid(publicKey, message))
    }

  }

  def prepareApiClient(enabled: Boolean = true): (ActorRef, HttpExt) = {
    val settings: RemoteKeysManagerSettings = mock[RemoteKeysManagerSettings]
    when(settings.enabled).thenReturn(enabled)
    when(settings.address).thenReturn("http://127.0.0.1:5000/api")

    val httpServerMock = mock[HttpExt]

    val apiClient = system.actorOf(Props(new SecureEnclaveApiClient(settings) {
      override private[client] val http = httpServerMock
    }))
    (apiClient, httpServerMock)
  }


  def generateKey(): SchnorrSecret = {
    val rnd = new Random()
    val vrfSeed = new Array[Byte](32)
    rnd.nextBytes(vrfSeed)
    SchnorrKeyGenerator.getInstance.generateSecret(vrfSeed)
  }
}
