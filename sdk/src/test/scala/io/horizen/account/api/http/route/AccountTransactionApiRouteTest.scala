package io.horizen.account.api.http.route

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import io.horizen.account.api.http.route.AccountTransactionRestScheme._
import io.horizen.account.utils.FeeUtils
import io.horizen.api.http.route.ErrorNotEnabledOnSeederNode
import io.horizen.api.http.route.TransactionBaseErrorResponse.ErrorByteTransactionParsing
import io.horizen.api.http.route.TransactionBaseRestScheme.{ReqAllTransactions, ReqSendTransaction}
import io.horizen.cryptolibprovider.CircuitTypes
import io.horizen.json.SerializationUtil
import io.horizen.params.MainNetParams
import io.horizen.proposition.VrfPublicKey
import io.horizen.utils.BytesUtils
import org.junit.Assert._
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar

import java.math.BigInteger
import java.util.Optional
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.Random

class AccountTransactionApiRouteTest extends AccountSidechainApiRouteTest with MockitoSugar {

  override val basePath = "/transaction/"

  //sdk/target/test-classes/ethereumtransaction_eoa2eoa_legacy_unsigned_hex
  val txUnsignedPayloadString: String =
    "01" + "e946824ce38252089470997970c51812dc3a010c7d01b50e0d17dc79c8888ac7230489e8000080808080"
  val fromAddressString =
    "dafea492d9c6733ae3d56b7ed1adb60692c98bc5"

  //sdk/target/test-classes/ethereumtransaction_eoa2eoa_legacy_signed_hex
  val txSignedPayloadString: String =
    "01" + "f86946824ce38252089470997970c51812dc3a010c7d01b50e0d17dc79c8888ac7230489e80000801ca02a4afbdd7e8d99c3df9dfd9e4ecd0afe018d8dec0b8b5fe1a44d5f30e7d0a5c5a07ca554a8317ff86eb6b23d06fa210d23e551bed58f58f803a87e5950aa47a9e9"
  val txSignedId =
    "6d4f0bc052e919019d392debb1a39724baa85b3d9c54836e9892170180793613"


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
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "allTransactions").withEntity("maybe_a_json") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }


      Post(basePath + "createLegacyEIP155Transaction").addCredentials(credentials) ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "createLegacyEIP155Transaction").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "createLegacyEIP155Transaction").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "createLegacyEIP155Transaction").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }

      Post(basePath + "makeForgerStake") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "makeForgerStake").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "makeForgerStake").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }

      Post(basePath + "spendForgingStake") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "spendForgingStake").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "spendForgingStake").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "withdrawCoins") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "withdrawCoins").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "withdrawCoins").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "createSmartContract") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
      }
      Post(basePath + "createSmartContract").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName)
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
        for (i <- transactionJsonNode.indices)
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
        for (i <- transactionIdsJsonNode.indices)
          assertEquals(memoryPool.get(i).id, transactionIdsJsonNode(i).asText())
      }
    }

    "reply at /createLegacyEIP155Transaction" in {
      Post(basePath + "createLegacyEIP155Transaction")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqLegacyTransaction(Option.apply("1234567890123456789012345678901234567890"),
          Option.apply("2234567890123456789012345678901234567890"), Some(BigInteger.ONE),
          BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, Option.apply(BigInteger.ONE),
          ""))) ~> sidechainTransactionApiRoute ~> check {
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
        for (i <- stakesJsonNode.indices)
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
        for (i <- stakesJsonNode.indices)
          jsonChecker.assertsOnAccountStakeInfoJson(stakesJsonNode(i), utilMocks.listOfStakes(i))
      }
    }

    "reply at /makeForgerStake" in {
      val stakeAmountInZennies = 32

      Post(basePath + "makeForgerStake")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqCreateForgerStake(Some(BigInteger.ONE),
        TransactionForgerOutput(utilMocks.ownerAddress.toStringNoPrefix, Some(utilMocks.blockSignerPropositionString), utilMocks.vrfPublicKeyString, stakeAmountInZennies), None))) ~> sidechainTransactionApiRoute ~> check {
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

      Post(basePath + "openForgerList").addCredentials(credentials).withEntity(SerializationUtil.serialize(outAsTransactionObj)) ~> sidechainTransactionApiRoute ~> check {
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
      Post(basePath + "createEIP1559Transaction").addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqEIP1559Transaction(None, Some("9835f7746494fcb0f81638480d46d03cb95922ff"),
            None, BigInteger.valueOf(230000), BigInteger.valueOf(900000000),
            BigInteger.valueOf(900000000), Some(BigInteger.valueOf(5000)), "", None, None, None))
        ) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals("0205", result.get("code").asText())
      }
    }

    "reply at /createLegacyTransaction" in {
      Post(basePath + "createLegacyTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqLegacyTransaction(Option.apply("1234567890123456789012345678901234567890"),
          Option.apply("2234567890123456789012345678901234567890"), Some(BigInteger.ONE),
          BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, Option.apply(BigInteger.ONE),
          ""))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals("0205", result.get("code").asText())
      }
    }

    "reply at /sendTransaction" in {
      //sdk/target/test-classes/ethereumtransaction_eoa2eoa_legacy_signed_hex
      Post(basePath + "sendTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqSendTransaction(
          txSignedPayloadString
        ))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for objecgit SidechainApiResponseBody")
        assertEquals(txSignedId, result.get("transactionId").asText())
      }
      // add trailing bytes after payload, it should fail
      Post(basePath + "sendTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSendTransaction(
        txSignedPayloadString + "abcd"
      ))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        // assert we got an error of the expected type
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorByteTransactionParsing("", Optional.empty()).code)
        // assert we have the expected specific error of that type
        val errMsg = mapper.readTree(entityAs[String]).get("error").get("detail").asText()
        assertTrue(errMsg.contains("Spurious bytes found"))
      }
    }

    "reply at /signTransaction" in {
      // insufficient balance on given 'from' address, should fail
      Post(basePath + "signTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqSignTransaction(
          Option.apply(fromAddressString), txUnsignedPayloadString
        ))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals("0205", result.get("code").asText())
      }
      // trailing bytes after payload, should fail
      Post(basePath + "signTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqSignTransaction(
          Option.apply(fromAddressString), txUnsignedPayloadString + "badbad"
        ))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        // assert we got an error of the expected type
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorByteTransactionParsing("", Optional.empty()).code)
        // assert we have the expected specific error of that type
        val errMsg = mapper.readTree(entityAs[String]).get("error").get("detail").asText()
        assertTrue(errMsg.contains("Spurious bytes found"))

      }
    }

    "reply at /createKeyRotationTransaction" in {
      Post(basePath + "createKeyRotationTransaction").addCredentials(credentials)
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

  "When isHandlingTransactionsEnabled = false API " should {
    Mockito.when(params.isHandlingTransactionsEnabled).thenReturn(false)
    val sidechainTransactionApiRoute: Route = AccountTransactionApiRoute(mockedRESTSettings,
      mockedSidechainNodeViewHolderRef, mockedSidechainTransactionActorRef,sidechainTransactionsCompanion, params,
      CircuitTypes.NaiveThresholdSignatureCircuit).route

    "reply at /allTransactions" in {
      // parameter 'format' = true
      Post(basePath + "allTransactions")
        .withEntity(SerializationUtil.serialize(ReqAllTransactions(None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        assertNotNull(result)
      }
    }

    "reply at /allWithdrawalRequests" in {
      val epochNum = 102
      Post(basePath + "allWithdrawalRequests").withEntity(SerializationUtil.serialize(ReqAllWithdrawalRequests(epochNum))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        assertNotNull(result)
      }
    }

    "reply at /allForgingStakes" in {
      Post(basePath + "allForgingStakes") ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        assertNotNull(result)
      }
    }


    "failed reply at /createLegacyEIP155Transaction" in {
      Post(basePath + "createLegacyEIP155Transaction")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqLegacyTransaction(Option.apply("1234567890123456789012345678901234567890"),
          Option.apply("2234567890123456789012345678901234567890"), Some(BigInteger.ONE),
          BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, Option.apply(BigInteger.ONE),
          ""))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }

    "failed reply at /createEIP1559Transaction" in {
      Post(basePath + "createEIP1559Transaction").addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqEIP1559Transaction(None, Some("9835f7746494fcb0f81638480d46d03cb95922ff"),
            None, BigInteger.valueOf(230000), BigInteger.valueOf(900000000),
            BigInteger.valueOf(900000000), Some(BigInteger.valueOf(5000)), "", None, None, None))
        ) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }


    "failed reply at /createLegacyTransaction" in {
      Post(basePath + "createLegacyTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqLegacyTransaction(Option.apply("1234567890123456789012345678901234567890"),
          Option.apply("2234567890123456789012345678901234567890"), Some(BigInteger.ONE),
          BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, Option.apply(BigInteger.ONE),
          ""))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }

    "reply at /sendTransaction" in {
      Post(basePath + "sendTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqSendTransaction(
          txSignedPayloadString
        ))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }

    "failed reply at /signTransaction" in {
      // insufficient balance on given 'from' address, should fail
      Post(basePath + "signTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqSignTransaction(
          Option.apply(fromAddressString), txUnsignedPayloadString
        ))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }


    "failed reply at /makeForgerStake" in {
      val stakeAmountInZennies = 32

      Post(basePath + "makeForgerStake")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqCreateForgerStake(Some(BigInteger.ONE),
          TransactionForgerOutput(utilMocks.ownerAddress.toStringNoPrefix, Some(utilMocks.blockSignerPropositionString), utilMocks.vrfPublicKeyString, stakeAmountInZennies), None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }

    "failed reply at /withdrawCoins" in {
      val amountInZennies = 32
      val mcAddr = BytesUtils.toHorizenPublicKeyAddress(utilMocks.getMCPublicKeyHashProposition.bytes(), params)
      Post(basePath + "withdrawCoins")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqWithdrawCoins(Some(BigInteger.ONE),
          TransactionWithdrawalRequest(mcAddr, amountInZennies), None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }

    "failed reply at /spendForgingStake" in {
      val outAsTransactionObj = ReqSpendForgingStake(Some(BigInteger.ONE),
        utilMocks.stakeId, None)

      Post(basePath + "spendForgingStake")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(outAsTransactionObj)) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)

      }
    }

    "failed reply at /createSmartContract" in {
      val contractCodeBytes = new Array[Byte](100)
      Random.nextBytes(contractCodeBytes)
      val contractCode = BytesUtils.toHexString(contractCodeBytes)
      Post(basePath + "createSmartContract")
        .addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqCreateContract(Some(BigInteger.ONE),
          contractCode, None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }


      "failed reply at /openForgerList" in {
      val outAsTransactionObj = ReqOpenStakeForgerList(Some(BigInteger.ONE), utilMocks.forgerIndex, None)

      Post(basePath + "openForgerList").addCredentials(credentials).withEntity(SerializationUtil.serialize(outAsTransactionObj)) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)

      }
    }

    "failed reply at /createKeyRotationTransaction" in {
      Post(basePath + "createKeyRotationTransaction").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(ReqCreateKeyRotationTransaction(1, 0, "123",
          "123", "123", "123",
          Option.apply(BigInteger.ONE), Option.apply(EIP1559GasInfo(
            BigInteger.valueOf(FeeUtils.GAS_LIMIT.longValue()), BigInteger.ONE, BigInteger.ONE)
          )))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotEnabledOnSeederNode().code)
      }
    }


  }

}
