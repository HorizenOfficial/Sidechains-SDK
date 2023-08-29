package io.horizen.api.http.client

import akka.actor.ActorSystem
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import io.horizen.fixtures.{CompanionsFixture, FieldElementFixture, SidechainBlockFixture}
import io.horizen.proof.SchnorrSignatureSerializer
import io.horizen.proposition.SchnorrPropositionSerializer
import io.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import io.horizen.utils.BytesUtils
import io.horizen.{RemoteKeysManagerSettings, SidechainTypes}
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, verifyNoInteractions, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.Random


@RunWith(classOf[JUnitRunner])
class SecureEnclaveApiClientTest extends AnyWordSpec with Matchers with MockitoSugar with SidechainBlockFixture with CompanionsFixture with SidechainTypes {

  implicit val system: ActorSystem = ActorSystem("test-api-client")
  implicit val timeout: Timeout = Timeout(1.second)
  private val keySerializer: SchnorrPropositionSerializer = SchnorrPropositionSerializer.getSerializer
  private val signatureSerializer: SchnorrSignatureSerializer = SchnorrSignatureSerializer.getSerializer
  private val mapper = new ObjectMapper()

  "Secure Enclave Api should " should {

    "tell if it is enabled" in {
      val (apiClient, serverMock) = prepareApiClient(false)
      apiClient.isEnabled shouldBe false
      verifyNoInteractions(serverMock)

      val (apiClientEnabled, mockEnabled) = prepareApiClient()
      apiClientEnabled.isEnabled shouldBe true
      verifyNoInteractions(mockEnabled)
    }


    "return empty future in case of error for listPublicKeys" in {
      val (apiClient, serverMock) = prepareApiClient()
      val response = mapper
        .createObjectNode()
        .put("error", "Do Androids Dream of Electric Sheep?")
        .toString

      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenReturn(Future.successful(
          HttpResponse(status = StatusCodes.OK, entity = response)
        ))

      val result = Await.result(apiClient.listPublicKeys(), 1.second)

      result shouldBe empty
      verify(serverMock).singleRequest(any(), any(), any(), any())
    }

    "return list of keys for listPublicKeys" in {
      val (apiClient, serverMock) = prepareApiClient()
      val key1 = generateKey().publicImage()
      val key2 = generateKey().publicImage()
      val key3 = generateKey().publicImage()
      val key1Json = mapper.createObjectNode()
        .put("publicKey", BytesUtils.toHexString(keySerializer.toBytes(key1)))
        .put("type", "schnorr")
      val key2Json = mapper.createObjectNode()
        .put("publicKey", BytesUtils.toHexString(keySerializer.toBytes(key2)))
        .put("type", "schnorr")
      val key3Json = mapper.createObjectNode()
        .put("publicKey", BytesUtils.toHexString(keySerializer.toBytes(key3)))
        .put("type", "schnorr")

      val response = mapper
          .createObjectNode()
          .set("keys", mapper.createArrayNode().add(key1Json).add(key2Json).add(key3Json))
          .toString

      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenReturn(Future.successful(
          HttpResponse(status = StatusCodes.OK, entity = response)
        ))

      val result = Await.result(apiClient.listPublicKeys(), 1.second)

      result should have size 3
      result should equal (Seq(key1, key2, key3))
      verify(serverMock).singleRequest(any(), any(), any(), any())
    }

    "return empty list for listPublicKeys if none" in {
      val (apiClient, serverMock) = prepareApiClient()

      val response = mapper
        .createObjectNode()
        .set("keys", mapper.createArrayNode())
        .toString

      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenReturn(Future.successful(
          HttpResponse(status = StatusCodes.OK, entity = response)
        ))

      val result = Await.result(apiClient.listPublicKeys(), 1.second)

      result shouldBe empty
      verify(serverMock).singleRequest(any(), any(), any(), any())
    }

    "return empty future in case of error for signWithEnclave" in {
      val (apiClient, serverMock) = prepareApiClient()
      val publicKey = generateKey().publicImage()
      val index = 1
      val response = mapper
        .createObjectNode()
        .put("error", "Do Androids Dream of Electric Sheep?")
        .toString

      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenReturn(Future.successful(
          HttpResponse(status = StatusCodes.OK, entity = response)
        ))

      val result = Await.result(
        apiClient.signWithEnclave("test".getBytes(StandardCharsets.UTF_8), (publicKey, index)), 1.second
      )

      result shouldBe empty
      verify(serverMock).singleRequest(any(), any(), any(), any())
    }

    "check request headers in signWithEnclave" in {
      val (apiClient, serverMock) = prepareApiClient()
      val publicKey = generateKey().publicImage()
      val index = 1
      val response = mapper
        .createObjectNode()
        .put("error", "Do Androids Dream of Electric Sheep?")
        .toString

      when(serverMock.singleRequest(any(), any(), any(), any()))
        .thenAnswer(invocationArguments => {
          assert(invocationArguments.getArgument(0).asInstanceOf[HttpRequest].getHeader("Accept").get().value() == "application/json")
          Future.successful(HttpResponse(status = StatusCodes.OK, entity = response))
        })

      Await.result(apiClient.signWithEnclave("test".getBytes(StandardCharsets.UTF_8), (publicKey, index)), 1.second)
      verify(serverMock).singleRequest(any(), any(), any(), any())
    }

    "check request headers in listPublicKeys" in {
      val (apiClient, serverMock) = prepareApiClient()

      val response = mapper
        .createObjectNode()
        .set("keys", mapper.createArrayNode())
        .toString

      when(serverMock.singleRequest(any(), any(), any(), any()))
        .thenAnswer(invocationArguments => {
          assert(invocationArguments.getArgument(0).asInstanceOf[HttpRequest].getHeader("Accept").get().value() == "application/json")
          Future.successful(HttpResponse(status = StatusCodes.OK, entity = response))
        })

      Await.result(apiClient.listPublicKeys(), 1.second)
      verify(serverMock).singleRequest(any(), any(), any(), any())
    }

    "return signature info with memorized index" in {
      val (apiClient, serverMock) = prepareApiClient()
      val message = FieldElementFixture.generateFieldElement()
      val privateKey = generateKey()
      val publicKey = privateKey.publicImage()
      val index = 1
      val response = mapper
        .createObjectNode()
        .put("signature", BytesUtils.toHexString(signatureSerializer.toBytes(privateKey.sign(message))))
        .toString

      when(serverMock.singleRequest(any(),any(),any(),any()))
        .thenReturn(Future.successful(
          HttpResponse(status = StatusCodes.OK, entity = response)
        ))

      val result = Await.result(
        apiClient.signWithEnclave(message, (publicKey, index)), 1.second
      )

      result shouldBe defined
      result.get.pubKeyIndex shouldBe index
      result.get.signature.isValid(publicKey, message) shouldBe true
    }

    "process several requests with errors between them signWithEnclave" in {
      val (apiClient, serverMock) = prepareApiClient()
      val message = FieldElementFixture.generateFieldElement()
      val privateKey = generateKey()
      val publicKey = privateKey.publicImage()
      val response = mapper
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
          apiClient.signWithEnclave(message, (publicKey, 1)),
          apiClient.signWithEnclave(message, (publicKey, 2)),
          apiClient.signWithEnclave(message, (publicKey, 3))
        )), 1.second
      )
        .flatten

      result should have size 2
      result.head.pubKeyIndex shouldBe 1
      result.head.signature.isValid(publicKey, message) shouldBe true
      result.last.pubKeyIndex shouldBe 3
      result.last.signature.isValid(publicKey, message) shouldBe true
    }

  }

  def prepareApiClient(enabled: Boolean = true): (SecureEnclaveApiClient, HttpExt) = {
    val settings: RemoteKeysManagerSettings = mock[RemoteKeysManagerSettings]
    when(settings.enabled).thenReturn(enabled)
    when(settings.address).thenReturn("http://127.0.0.1:5000")

    val httpServerMock = mock[HttpExt]

    val apiClient = new SecureEnclaveApiClient(settings) {
      override private[client] val http = httpServerMock
    }
    (apiClient, httpServerMock)
  }


  def generateKey(): SchnorrSecret = {
    val rnd = new Random()
    val vrfSeed = new Array[Byte](32)
    rnd.nextBytes(vrfSeed)
    SchnorrKeyGenerator.getInstance.generateSecret(vrfSeed)
  }
}
