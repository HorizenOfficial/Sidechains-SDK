package com.horizen.account.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.account.api.http.AccountTransactionRestScheme._
import com.horizen.account.utils.FeeUtils
import com.horizen.proposition.VrfPublicKey
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.mockito.Mockito

import java.math.BigInteger
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.Random

class AccountTransactionApiRouteTest extends AccountSidechainApiRouteTest {

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

      Post(basePath + "allTransactions").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allTransactions").withEntity("maybe_a_json") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }


      Post(basePath + "sendCoinsToAddress").addCredentials(credentials) ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "sendCoinsToAddress").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "sendCoinsToAddress").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "sendCoinsToAddress").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }

      Post(basePath + "makeForgerStake") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "makeForgerStake").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "makeForgerStake").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }

      Post(basePath + "spendForgingStake") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "spendForgingStake").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "spendForgingStake").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "withdrawCoins") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "withdrawCoins").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "withdrawCoins").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "createSmartContract") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createSmartContract").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createSmartContract").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
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
        .addCredentials(credentials)
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
      Post(basePath + "withdrawCoins")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqWithdrawCoins(Some(BigInteger.ONE),
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

      Post(basePath + "makeForgerStake")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqCreateForgerStake(Some(BigInteger.ONE),
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

      Post(basePath + "spendForgingStake")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(outAsTransactionObj)) ~> sidechainTransactionApiRoute ~> check {
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
      Post(basePath + "createSmartContract")
        .addCredentials(credentials)
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

    "reply at /createEIP1559Transaction" in {
      Post(basePath + "createEIP1559Transaction").withHeaders(apiTokenHeader)
        .withEntity(SerializationUtil.serialize(ReqEIP1559Transaction(Option.apply("1234567890123456789012345678901234567890"),
          Option.apply("2234567890123456789012345678901234567890"), Option.apply(BigInteger.ONE),
          BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, BigInteger.ONE,
          BigInteger.ONE, "", None, None,
          None)))~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals("0205", result.get("code").asText())
      }
    }

    "reply at /createLegacyTransaction" in {
      Post(basePath + "createLegacyTransaction").withHeaders(apiTokenHeader)
        .withEntity(SerializationUtil.serialize(ReqLegacyTransaction(Option.apply("1234567890123456789012345678901234567890"),
          Option.apply("2234567890123456789012345678901234567890"), BigInteger.ONE,
          BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, Option.apply(BigInteger.ONE),
          "", None, None,
          None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals("0205", result.get("code").asText())
      }
    }

    "reply at /sendRawTransaction" in {
      //https://etherscan.io/tx/0xbb9f10e90640b689297e171694f11b7d8dc417f7d39a03ea9ccfa19d3ecd78b2
      Post(basePath + "sendRawTransaction").withHeaders(apiTokenHeader)
        .withEntity(SerializationUtil.serialize(ReqRawTransaction(Option.apply("dafea492d9c6733ae3d56b7ed1adb60692c98bc5"),
        "02f871018302c64b8085037601bcd782565f94388c818ca8b9251b393131c08a736a67ccb192978744307408638cc980c080a0b914c0f8359e7dfa7a8d3712a22d699d5fa8b1fe2e0668deddb2e70f741d17f2a06d89bd666df886aa6d9a65f336568f0c406a61640c45b79af45a4c22e5e3ba2a"))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for objecgit SidechainApiResponseBody")

        assertEquals("bb9f10e90640b689297e171694f11b7d8dc417f7d39a03ea9ccfa19d3ecd78b2", result.get("transactionId").asText())
      }
    }

    "reply at /signTransaction" in {
      //https://etherscan.io/tx/0xbb9f10e90640b689297e171694f11b7d8dc417f7d39a03ea9ccfa19d3ecd78b2
      // with insufficient balance
      Post(basePath + "signTransaction").withHeaders(apiTokenHeader)
        .withEntity(SerializationUtil.serialize(ReqRawTransaction(Option.apply("dafea492d9c6733ae3d56b7ed1adb60692c98bc5"),
          "02f871018302c64b8085037601bcd782565f94388c818ca8b9251b393131c08a736a67ccb192978744307408638cc980c080a0b914c0f8359e7dfa7a8d3712a22d699d5fa8b1fe2e0668deddb2e70f741d17f2a06d89bd666df886aa6d9a65f336568f0c406a61640c45b79af45a4c22e5e3ba2a"))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals("0205", result.get("code").asText())
      }
    }

    "reply at /createKeyRotationTransaction" in {
      Post(basePath + "createKeyRotationTransaction").withHeaders(apiTokenHeader)
        .withEntity(SerializationUtil.serialize(ReqCreateKeyRotationTransaction(1, 0, "123",
          "123", "123", "123",
          Option.apply(BigInteger.ONE), Option.apply(EIP1559GasInfo(
            BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, BigInteger.ONE)
          )))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals("0404", result.get("code").asText())
      }
    }
  }
}
