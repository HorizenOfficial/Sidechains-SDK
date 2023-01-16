package com.horizen.account.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.account.api.http.AccountTransactionRestScheme._
import com.horizen.proposition.VrfPublicKey
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.mockito.Mockito
import org.scalatest.prop.TableDrivenPropertyChecks

import java.math.BigInteger
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.Random

class AccountTransactionApiRouteTest extends AccountSidechainApiRouteTest with TableDrivenPropertyChecks {

  override val basePath = "/transaction/"

  "The Api should" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainTransactionApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      val cases = Table(
        ("Route", "Expected response code"),
        ("allTransactions", StatusCodes.OK.intValue),
        ("allWithdrawalRequests", StatusCodes.OK.intValue),
        ("allForgingStakes", StatusCodes.OK.intValue),
        ("sendCoinsToAddress", StatusCodes.BadRequest.intValue),
        ("createEIP1559Transaction", StatusCodes.BadRequest.intValue),
        ("createLegacyTransaction", StatusCodes.BadRequest.intValue),
        ("sendRawTransaction", StatusCodes.InternalServerError.intValue),
        ("signTransaction", StatusCodes.InternalServerError.intValue),
        ("makeForgerStake", StatusCodes.BadRequest.intValue),
        ("withdrawCoins", StatusCodes.BadRequest.intValue),
        ("spendForgingStake", StatusCodes.BadRequest.intValue),
        ("createSmartContract", StatusCodes.BadRequest.intValue),
        ("decodeTransactionBytes", StatusCodes.InternalServerError.intValue),
        ("createKeyRotationTransaction", StatusCodes.BadRequest.intValue)
      )

      forAll(cases) { (route, expectedCode) =>
        val path = basePath + route

        if (expectedCode == StatusCodes.BadRequest.intValue) {
          Post(path).withHeaders(apiTokenHeader) ~> sidechainTransactionApiRoute ~> check {
            rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
          }
          Post(path).withHeaders(apiTokenHeader).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
            rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
          }
        }

        Post(path).withHeaders(apiTokenHeader) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
          status.intValue() shouldBe expectedCode
          if (expectedCode != StatusCodes.InternalServerError.intValue && expectedCode != StatusCodes.NotFound.intValue) {
            responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
          }
        }

        if (expectedCode != StatusCodes.NotFound.intValue && route != "allWithdrawalRequests" && route != "allForgingStakes") {
          Post(path).withHeaders(badApiTokenHeader).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
            rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
          }
        }
      }
    }

    "reply at /allTransactions" in {
      // parameter 'format' = true
      Post(basePath + "allTransactions")
        .withEntity(SerializationUtil.serialize(ReqAllTransactions(None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactions").isArray)
        assertEquals(memoryPool.size(), result.get("transactions").elements().asScala.length)
        val transactionJsonNode = result.get("transactions").elements().asScala.toList
        for (i <- 0 to transactionJsonNode.size - 1)
          jsonChecker.assertsOnAccountTransactionJson(transactionJsonNode(i), memoryPool.get(i))
      }
      // parameter 'format' = false
      Post(basePath + "allTransactions")
        .withEntity(SerializationUtil.serialize(ReqAllTransactions(Some(false)))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionIds").isArray)
        assertEquals(memoryPool.size(), result.get("transactionIds").elements().asScala.length)
        val transactionIdsJsonNode = result.get("transactionIds").elements().asScala.toList
        for (i <- 0 to transactionIdsJsonNode.size - 1)
          assertEquals(memoryPool.get(i).id, transactionIdsJsonNode(i).asText())
      }
    }


    "reply at /sendCoinsToAddress" in {
      sidechainApiMockConfiguration.setShould_history_getTransactionsSortedByFee_return_value(true)
      Post(basePath + "sendCoinsToAddress")
        .withHeaders(apiTokenHeader)
        .withEntity(
          SerializationUtil.serialize(ReqSendCoinsToAddress(Option.empty[String], None,
            "00112233445566778899AABBCCDDEEFF01020304", 10, Option.empty[Boolean], Option.empty[EIP1559GasInfo]))
        ) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /allWithdrawalRequests" in {
      val epochNum = 102
      Post(basePath + "allWithdrawalRequests").withEntity(SerializationUtil.serialize(ReqAllWithdrawalRequests(epochNum))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("listOfWR").isArray)
        assertEquals(1, result.get("listOfWR").elements().asScala.length)
        val stakesJsonNode = result.get("listOfWR").elements().asScala.toList
        for (i <- 0 to stakesJsonNode.size - 1)
          jsonChecker.assertsOnWithdrawalRequestJson(stakesJsonNode(i), utilMocks.listOfWithdrawalRequests(i))
      }
    }

    "reply at /withdrawCoins" in {
      val amountInZennies = 32
      val mcAddr = BytesUtils.toHorizenPublicKeyAddress(utilMocks.getMCPublicKeyHashProposition.bytes(),params)
      Post(basePath + "withdrawCoins").withHeaders(apiTokenHeader).withEntity(SerializationUtil.serialize(ReqWithdrawCoins(Some(BigInteger.ONE),
        TransactionWithdrawalRequest(mcAddr, amountInZennies), None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionId").isTextual)
      }
    }


    "reply at /allForgingStakes" in {
      Post(basePath + "allForgingStakes") ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("stakes").isArray)
        assertEquals(utilMocks.listOfStakes.size, result.get("stakes").elements().asScala.length)
        val stakesJsonNode = result.get("stakes").elements().asScala.toList
        for (i <- 0 to stakesJsonNode.size - 1)
          jsonChecker.assertsOnAccountStakeInfoJson(stakesJsonNode(i), utilMocks.listOfStakes(i))
      }
    }

    "reply at /makeForgerStake" in {
      val stakeAmountInZennies = 32

      Post(basePath + "makeForgerStake").withHeaders(apiTokenHeader).withEntity(SerializationUtil.serialize(ReqCreateForgerStake(Some(BigInteger.ONE),
        TransactionForgerOutput(utilMocks.ownerPublicKeyString, Some(utilMocks.blockSignerPropositionString), utilMocks.vrfPublicKeyString, stakeAmountInZennies), None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionId").isTextual)
      }
    }

    "reply at /spendForgingStake" in {
      val outAsTransactionObj = ReqSpendForgingStake(Some(BigInteger.ONE),
        utilMocks.stakeId, None)

      Post(basePath + "spendForgingStake").withHeaders(apiTokenHeader).withEntity(SerializationUtil.serialize(outAsTransactionObj)) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionId").isTextual)

      }
    }

    // setup a mocked conf with a restricted forger list
    Mockito.when(params.restrictForgers).thenReturn(true)
    val blockSignerProposition = utilMocks.signerSecret.publicImage()
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes
    Mockito.when(params.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))

    "reply at /openForgerList" in {
      val outAsTransactionObj = ReqOpenStakeForgerList(Some(BigInteger.ONE), utilMocks.forgerIndex, None)

      Post(basePath + "openForgerList").withHeaders(apiTokenHeader).withEntity(SerializationUtil.serialize(outAsTransactionObj)) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail(s"Serialization failed for object SidechainApiResponseBody: ${mapper.readTree(entityAs[String])}")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionId").isTextual)

      }
    }

    "reply at /createSmartContract" in {
      val contractCodeBytes = new Array[Byte](100)
      Random.nextBytes(contractCodeBytes)
      val contractCode = BytesUtils.toHexString(contractCodeBytes)
      Post(basePath + "createSmartContract").withHeaders(apiTokenHeader)
        .withEntity(SerializationUtil.serialize(ReqCreateContract(Some(BigInteger.ONE),
        contractCode, None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionId").isTextual)
      }
    }

  }
}
