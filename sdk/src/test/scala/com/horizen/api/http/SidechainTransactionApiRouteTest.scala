package com.horizen.api.http

import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import com.horizen.api.http.SidechainTransactionErrorResponse.{ErrorByteTransactionParsing, ErrorNotFoundTransactionId, ErrorNotFoundTransactionInput, GenericTransactionError}
import com.horizen.api.http.SidechainTransactionRestScheme._
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.serialization.SerializationUtil
import com.horizen.transaction.RegularTransactionSerializer
import com.horizen.utils.BytesUtils
import org.junit.Assert._

import scala.collection.JavaConverters._
import java.util.{Optional => JOptional}

class SidechainTransactionApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/transaction/"

  "The Api should to" should {

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

      Post(basePath + "findById").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "findById").withEntity("maybe_a_json") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "decodeTransactionBytes").withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "decodeTransactionBytes").withEntity("maybe_a_json") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }

      Post(basePath + "createCoreTransaction").addCredentials(credentials) ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createCoreTransaction").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createCoreTransaction").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "createCoreTransaction").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }

      Post(basePath + "createCoreTransactionSimplified").addCredentials(credentials) ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createCoreTransactionSimplified").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "createCoreTransactionSimplified").addCredentials(credentials) ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "createCoreTransactionSimplified").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
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

      Post(basePath + "sendTransaction").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "sendTransaction").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainTransactionApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "sendTransaction").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }

      Post(basePath + "spendForgingStake").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }

      Post(basePath + "makeForgerStake").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }

      Post(basePath + "withdrawCoins").addCredentials(badCredentials).withEntity("maybe_a_json") ~> sidechainTransactionApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
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
          jsonChecker.assertsOnTransactionJson(transactionJsonNode(i), memoryPool.get(i))
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

    "reply at /findById" in {
      val transactionFound = memoryPool.get(0)
      val transactionIdNotValid = BytesUtils.toHexString("transactionId".getBytes)
      val transactionIdValid = transactionFound.id
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool not found
      // ERROR
      sidechainApiMockConfiguration.setShould_memPool_searchTransactionInMemoryPool_return_value(false)
      sidechainApiMockConfiguration.setShould_history_searchTransactionInBlockchain_return_value(false)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdNotValid, None, None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotFoundTransactionId("", JOptional.empty()).code)
      }
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool not found
      // parameter 'format' = false
      sidechainApiMockConfiguration.setShould_memPool_searchTransactionInMemoryPool_return_value(false)
      sidechainApiMockConfiguration.setShould_history_searchTransactionInBlockchain_return_value(true)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, None, None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        assertEquals(result, null)
      }
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool not found
      // parameter 'format' = true
      sidechainApiMockConfiguration.setShould_memPool_searchTransactionInMemoryPool_return_value(false)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, None, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        assertEquals(result, null)
      }
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool found
      // parameter 'format' = false
      sidechainApiMockConfiguration.setShould_memPool_searchTransactionInMemoryPool_return_value(true)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, None, None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionBytes").isTextual)
        assertEquals(BytesUtils.toHexString(sidechainTransactionsCompanion.toBytes(transactionFound)), result.get("transactionBytes").asText())
      }
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool found
      // parameter 'format' = true
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, None, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transaction").isObject)
        jsonChecker.assertsOnTransactionJson(result.get("transaction"), transactionFound)
      }
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool found
      // parameter 'format' = false
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, None, None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transactionBytes").isTextual)
        assertEquals(BytesUtils.toHexString(sidechainTransactionsCompanion.toBytes(transactionFound)), result.get("transactionBytes").asText())
      }
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool found
      // parameter 'format' = true
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, None, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transaction").isObject)
        jsonChecker.assertsOnTransactionJson(result.get("transaction"), transactionFound)
      }
      // Case --> blockHash not set -> Search in memory pool
      // searchTransactionInMemoryPool not found
      // ERROR
      sidechainApiMockConfiguration.setShould_memPool_searchTransactionInMemoryPool_return_value(false)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdNotValid, None, None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotFoundTransactionId("", JOptional.empty()).code)
      }
      // Case --> blockHash set -> Search in block referenced by blockHash
      // searchTransactionInBlock not found
      // ERROR
      sidechainApiMockConfiguration.setShould_history_searchTransactionInBlock_return_value(false)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdNotValid, Some("blockHash"), None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotFoundTransactionId("", JOptional.empty()).code)
      }
      // Case --> blockHash set -> Search in block referenced by blockHash
      // searchTransactionInBlock found
      // parameter 'format' = false
      sidechainApiMockConfiguration.setShould_history_searchTransactionInBlock_return_value(true)
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, Some("blockHash"), None))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      // Case --> blockHash set -> Search in block referenced by blockHash
      // searchTransactionInBlock found
      // parameter 'format' = true
      Post(basePath + "findById")
        .withEntity(SerializationUtil.serialize(ReqFindById(transactionIdValid, Some("blockHash"), Some(true)))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("transaction").isObject)
        jsonChecker.assertsOnTransactionJson(result.get("transaction"), transactionFound)
      }
    }

    "reply at /decodeTransactionBytes" in {
      Post(basePath + "decodeTransactionBytes")
        .withEntity(SerializationUtil.serialize(ReqDecodeTransactionBytes(
          BytesUtils.toHexString(sidechainTransactionsCompanion.toBytes(memoryPool.get(0)))))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        val tNode = result.get("transaction")
        jsonChecker.assertsOnTransactionJson(tNode)
      }
      // companion.parseBytesTry -> FAILURE
      Post(basePath + "decodeTransactionBytes")
        .withEntity(SerializationUtil.serialize(ReqDecodeTransactionBytes(
          BytesUtils.toHexString(RegularTransactionSerializer.getSerializer.toBytes(memoryPool.get(0)))))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorByteTransactionParsing("", JOptional.empty()).code)
      }
      // BytesUtils.fromHexString -> ERROR
      Post(basePath + "decodeTransactionBytes")
        .withEntity(SerializationUtil.serialize(ReqDecodeTransactionBytes("AAABBBCCC"))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`text/plain(UTF-8)`
      }
    }

    "reply at /createCoreTransaction" in {
      // parameter 'format' = true
      val transactionInput: List[TransactionInput] = List(utilMocks.box_1.id(), utilMocks.box_2.id(), utilMocks.box_3.id()).map(id => TransactionInput(BytesUtils.toHexString(id)))
      val transactionOutput: List[TransactionOutput] = List(TransactionOutput(BytesUtils.toHexString(utilMocks.box_1.proposition().bytes), 30))
      val withdrawalRequests: List[TransactionWithdrawalRequestOutput] = List()
      val forgerOutputs: List[TransactionForgerOutput] = List()

      Post(basePath + "createCoreTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqCreateCoreTransaction(transactionInput, transactionOutput, withdrawalRequests, forgerOutputs, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
        //println(response)
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        val tNode = result.get("transaction")
        jsonChecker.assertsOnTransactionJson(tNode)
      }
      // parameter 'format' = false
      Post(basePath + "createCoreTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqCreateCoreTransaction(transactionInput, transactionOutput, withdrawalRequests, forgerOutputs, Some(false)))) ~> sidechainTransactionApiRoute ~> check {
        println(response)
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        try {
          BytesUtils.fromHexString(result.get("transactionBytes").asText())
        } catch {
          case _: Throwable => fail()
        }
      }
      val transactionInput_2: List[TransactionInput] = transactionInput :+ TransactionInput("a_boxId")
      Post(basePath + "createCoreTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqCreateCoreTransaction(transactionInput_2, transactionOutput, withdrawalRequests, forgerOutputs, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
        //println(response)
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotFoundTransactionInput("", JOptional.empty()).code)
      }
      Post(basePath + "createCoreTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqCreateCoreTransaction(List(transactionInput_2.head), transactionOutput, withdrawalRequests, forgerOutputs, None))) ~> sidechainTransactionApiRoute ~> check {
        println(response)
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], GenericTransactionError("", JOptional.empty()).code)
      }
    }

    "reply at /sendCoinsToAddress" in {
      sidechainApiMockConfiguration.setShould_history_getTransactionsSortedByFee_return_value(true)
      val transactionOutput: List[TransactionOutput] = List(TransactionOutput(BytesUtils.toHexString(allBoxes.asScala.head.proposition().asInstanceOf[PublicKey25519Proposition].bytes), 2))
      Post(basePath + "sendCoinsToAddress")
        .addCredentials(credentials)
        .withEntity(
          //"{\"outputs\": [{\"publicKey\": \"sadasdasfsdfsdfsdf\",\"value\": 12}],\"fee\": 30}"
          SerializationUtil.serialize(ReqSendCoinsToAddress(transactionOutput, None, None, None))
        ) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /spendForgingStake" in {
        // parameter 'format' = true
        // Spend 1 forger box to create 1 regular box and 1 forger box
        val transactionInput: List[TransactionInput] = List(utilMocks.box_4.id()).map(id => TransactionInput(BytesUtils.toHexString(id)))
        val regularOutputs: List[TransactionOutput] = List(TransactionOutput(BytesUtils.toHexString(utilMocks.box_1.proposition().bytes), 10))
        val forgerOutputs: List[TransactionForgerOutput] = List(TransactionForgerOutput(
          BytesUtils.toHexString(utilMocks.box_1.proposition().bytes),
          None,
          BytesUtils.toHexString(utilMocks.box_4.vrfPubKey().bytes),
          10))

        Post(basePath + "spendForgingStake")
          .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSpendForgingStake(transactionInput, regularOutputs, forgerOutputs, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
          println(response)
          status.intValue() shouldBe StatusCodes.OK.intValue
          responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        }
        // parameter 'format' = false
        Post(basePath + "spendForgingStake")
          .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSpendForgingStake(transactionInput, regularOutputs, forgerOutputs, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
          println(response)
          status.intValue() shouldBe StatusCodes.OK.intValue
          responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        }
        val transactionInput_2: List[TransactionInput] = transactionInput :+ TransactionInput("a_boxId")
        Post(basePath + "spendForgingStake")
          .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSpendForgingStake(transactionInput_2, regularOutputs, forgerOutputs, Some(true)))) ~> sidechainTransactionApiRoute ~> check {
          println(response)
          status.intValue() shouldBe StatusCodes.OK.intValue
          responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
          assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorNotFoundTransactionInput("", JOptional.empty()).code)
        }
    }

    "reply at /sendTransaction" in {
      val transaction = memoryPool.get(0)
      val transactionBytes = sidechainTransactionsCompanion.toBytes(transaction)
      // parameter 'format' = true
      sidechainApiMockConfiguration.setShould_transactionActor_BroadcastTransaction_reply(true)
      Post(basePath + "sendTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSendTransactionPost(BytesUtils.toHexString(transactionBytes)))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.elements().asScala.length)
        try {
          BytesUtils.fromHexString(result.get("transactionId").asText())
        } catch {
          case _: Throwable => fail()
        }
      }
      // BytesUtils.fromHexString(body.transactionBytes) -> ERROR
      Post(basePath + "sendTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSendTransactionPost("SOMEBYTES"))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`text/plain(UTF-8)`
      }
      // companion.parseBytesTry(transactionBytes) -> FAILURE
      Post(basePath + "sendTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSendTransactionPost(BytesUtils.toHexString(RegularTransactionSerializer.getSerializer.toBytes(transaction))))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], GenericTransactionError("", JOptional.empty()).code)
      }
      sidechainApiMockConfiguration.setShould_transactionActor_BroadcastTransaction_reply(false)
      Post(basePath + "sendTransaction")
        .addCredentials(credentials).withEntity(SerializationUtil.serialize(ReqSendTransactionPost(BytesUtils.toHexString(transactionBytes)))) ~> sidechainTransactionApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], GenericTransactionError("", JOptional.empty()).code)
      }
    }
  }
}