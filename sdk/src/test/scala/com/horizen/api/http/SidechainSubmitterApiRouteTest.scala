package com.horizen.api.http

import akka.http.scaladsl.server.MalformedRequestContentRejection
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import com.horizen.api.http.route.SidechainDebugErrorResponse.ErrorBadCircuit
import com.horizen.api.http.route.SidechainDebugRestScheme.{ReqGetKeyRotationMessageToSign, ReqKeyRotationProof}
import com.horizen.json.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue}

import java.util.{Optional => JOptional}
import scala.collection.JavaConverters._
import scala.language.postfixOps

class SidechainSubmitterApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/submitter/"

  "The Api should to" should {

    "reply at /getKeyRotationProofs" in {
      //Malformed request
      Post(basePath + "getKeyRotationProof").withEntity("maybe_a_json") ~> sidechainSubmitterApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }

      //Bad circuit
      Post(basePath + "getKeyRotationProof").withEntity(SerializationUtil.serialize(ReqKeyRotationProof(0, 0, 0))) ~> sidechainSubmitterApiRoute ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBadCircuit("The current circuit doesn't support key rotation proofs!", JOptional.empty()).code)
      }

      //Should answer with a KeyRotationProof
      Post(basePath + "getKeyRotationProof").withEntity(SerializationUtil.serialize(ReqKeyRotationProof(0, 0, 0))) ~> sidechainSubmitterApiRouteWithKeyRotation ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements.asScala.length)

        val keyRotationProofJson = result.get("keyRotationProof")
        assertTrue(keyRotationProofJson.has("keyType"))
        assertEquals("SigningKeyRotationProofType", keyRotationProofJson.get("keyType").get("value").asText())
        assertEquals(keyRotationProof.index, keyRotationProofJson.get("index").asInt())
        assertTrue(keyRotationProofJson.has("newKey"))
        assertEquals(BytesUtils.toHexString(keyRotationProof.newKey.pubKeyBytes()), keyRotationProofJson.get("newKey").get("publicKey").asText())
        assertTrue(keyRotationProofJson.has("signingKeySignature"))
        assertEquals(BytesUtils.toHexString(keyRotationProof.signingKeySignature.bytes()), keyRotationProofJson.get("signingKeySignature").get("signature").asText())
        assertTrue(keyRotationProofJson.has("masterKeySignature"))
        assertEquals(BytesUtils.toHexString(keyRotationProof.masterKeySignature.bytes()), keyRotationProofJson.get("masterKeySignature").get("signature").asText())
      }
    }

    "reply at /getCertifiersKeys" in {

      //Should answer with a CertificateKeys
      Post(basePath + "getCertifiersKeys") ~> sidechainSubmitterApiRouteWithKeyRotation ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(2, result.elements.asScala.length)

        val certifiersKeysJson = result.get("certifiersKeys")
        assertTrue(certifiersKeysJson.has("signingKeys"))
        val signingKeys = certifiersKeysJson.get("signingKeys")
        assertEquals(certifiersKeys.signingKeys.size, signingKeys.size())
        assertEquals(BytesUtils.toHexString(certifiersKeys.signingKeys(0).pubKeyBytes()), signingKeys.get(0).get("publicKey").asText())
        assertEquals(BytesUtils.toHexString(certifiersKeys.signingKeys(1).pubKeyBytes()), signingKeys.get(1).get("publicKey").asText())

        assertTrue(certifiersKeysJson.has("masterKeys"))
        val masterKeys = certifiersKeysJson.get("masterKeys")
        assertEquals(certifiersKeys.masterKeys.size, masterKeys.size())
        assertEquals(BytesUtils.toHexString(certifiersKeys.masterKeys(0).pubKeyBytes()), masterKeys.get(0).get("publicKey").asText())
        assertEquals(BytesUtils.toHexString(certifiersKeys.masterKeys(1).pubKeyBytes()), masterKeys.get(1).get("publicKey").asText())
      }

    }

    "reply at /getKeyRotationMessageToSignForSigningKey" in {
      //Malformed request
      Post(basePath + "getKeyRotationMessageToSignForSigningKey").withEntity("maybe_a_json") ~> sidechainSubmitterApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }

      val byteArray: Array[Byte] = getSchnorrKey.getPublicBytes
      val key = BytesUtils.toHexString(byteArray)
      //Bad circuit
      Post(basePath + "getKeyRotationMessageToSignForSigningKey").withEntity(SerializationUtil.serialize(ReqGetKeyRotationMessageToSign(key, 0))) ~> sidechainSubmitterApiRoute ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBadCircuit("The current circuit doesn't support key rotation message to sign!", JOptional.empty()).code)
      }

      //Should answer with a KeyRotationProof
      Post(basePath + "getKeyRotationMessageToSignForSigningKey").withEntity(SerializationUtil.serialize(ReqGetKeyRotationMessageToSign(key, 0))) ~> sidechainSubmitterApiRouteWithKeyRotation ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements.asScala.length)

        val keyRotationMessageToSign = result.get("keyRotationMessageToSign")
        assertTrue(keyRotationMessageToSign != null)
        assertEquals("Length of message should be 64", 64, keyRotationMessageToSign.asText().length)
      }
    }

    "reply at /getKeyRotationMessageToSignForMasterKey" in {
      //Malformed request
      Post(basePath + "getKeyRotationMessageToSignForMasterKey").withEntity("maybe_a_json") ~> sidechainSubmitterApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }

      val byteArray: Array[Byte] = getSchnorrKey.getPublicBytes
      val key = BytesUtils.toHexString(byteArray)
      //Bad circuit
      Post(basePath + "getKeyRotationMessageToSignForMasterKey").withEntity(SerializationUtil.serialize(ReqGetKeyRotationMessageToSign(key, 0))) ~> sidechainSubmitterApiRoute ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorBadCircuit("The current circuit doesn't support key rotation message to sign!", JOptional.empty()).code)
      }

      //Should answer with a KeyRotationProof
      Post(basePath + "getKeyRotationMessageToSignForSigningKey").withEntity(SerializationUtil.serialize(ReqGetKeyRotationMessageToSign(key, 0))) ~> sidechainSubmitterApiRouteWithKeyRotation ~> check {
        status.intValue shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements.asScala.length)

        val keyRotationMessageToSign = result.get("keyRotationMessageToSign")
        assertTrue(keyRotationMessageToSign != null)
        assertEquals("Length of message should be 64", 64, keyRotationMessageToSign.asText().length)
      }
    }
  }
}