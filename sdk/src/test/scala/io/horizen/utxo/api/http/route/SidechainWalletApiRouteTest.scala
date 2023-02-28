package io.horizen.utxo.api.http.route

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import io.horizen.api.http.route.SidechainApiRouteTest
import io.horizen.api.http.route.WalletBaseErrorResponse.{ErrorPropositionNotFound, ErrorSecretAlreadyPresent, ErrorSecretNotAdded}
import io.horizen.api.http.route.WalletBaseRestScheme.{ReqAllPropositions, ReqDumpSecrets, ReqExportSecret, ReqImportSecret}
import io.horizen.json.SerializationUtil
import io.horizen.utils.BytesUtils
import io.horizen.utxo.api.http.route.SidechainWalletRestScheme._
import org.junit.Assert._

import java.io.File
import java.util.{Scanner, Optional => JOptional}
import scala.collection.JavaConverters._

class SidechainWalletApiRouteTest extends SidechainApiRouteTest {

  override val basePath = "/wallet/"

  "The Api" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainWalletApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "createPrivateKey25519").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "createVrfSecret").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "allBoxes").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allBoxes").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "allBoxes").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "coinsBalance").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "balanceOfType").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "balanceOfType").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "balanceOfType").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "allPublicKeys").withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "allPublicKeys").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "allPublicKeys").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }


      Post(basePath + "importSecret").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "importSecret").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "importSecret").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "exportSecret").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "exportSecret").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "exportSecret").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "dumpSecrets").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "dumpSecrets").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "dumpSecrets").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }

      Post(basePath + "importSecrets").addCredentials(credentials).withEntity("maybe_a_json") ~> sidechainWalletApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      Post(basePath + "importSecrets").addCredentials(credentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.BadRequest.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "importSecrets").addCredentials(badCredentials).withEntity("maybe_a_json") ~> Route.seal(sidechainWalletApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.Unauthorized.intValue
      }
    }

    "reply at /allBoxes" in {
      Post(basePath + "allBoxes").addCredentials(credentials) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("boxes").size())
        val node = result.get("boxes")
        if (node == null)
          fail("Result serialization failed")
        assertTrue(node.isArray)
        assertEquals(allBoxes.size(), node.elements().asScala.length)
        val box_json = node.elements().asScala.toList
        for (i <- 0 to box_json.size - 1)
          jsonChecker.assertsOnBoxJson(box_json(i), allBoxes.get(i))
      }
      Post(basePath + "allBoxes")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxes(Some("a_boxTypeClass"), None))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "allBoxes")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxes(None, Some(Seq(
            BytesUtils.toHexString(allBoxes.get(0).id()), BytesUtils.toHexString(allBoxes.get(1).id())))))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("boxes").size())
        val node = result.get("boxes")
        if (result == null)
          fail("Result serialization failed")

        assertTrue(node.isArray)
        assertEquals(allBoxes.size() - 2, node.elements().asScala.length)
      }
      Post(basePath + "allBoxes")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqAllBoxes(Some("a_boxTypeClass"), Some(Seq("boxId_1", "boxId_2"))))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`text/plain(UTF-8)`
      }
    }

    "reply at /coinsBalance" in {
      Post(basePath + "coinsBalance").addCredentials(credentials) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.fieldNames().asScala.length)

        val node = result.get("balance")
        if (node == null)
          fail("Result serialization failed")

        node.asInt() shouldBe 5500
      }
    }

    "reply at /balanceOfType" in {
      Post(basePath + "balanceOfType")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqBalance("a_class"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /createVrfSecret" in {
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      // secret is added
      Post(basePath + "createVrfSecret").addCredentials(credentials) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("proposition").size())
        assertEquals(1, result.path("proposition").findValues("publicKey").size())
        val node = result.get("proposition").get("publicKey")
        if (node == null)
          fail("Result serialization failed")

        assertTrue(node.isTextual)
      }
      // secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_GenerateSecret_reply(false)
      Post(basePath + "createVrfSecret").addCredentials(credentials) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretNotAdded("", JOptional.empty()).code)
      }
    }

    "reply at /createPrivateKey25519" in {
      // secret is added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      sidechainApiMockConfiguration.setShould_nodeViewHolder_GenerateSecret_reply(true)
      Post(basePath + "createPrivateKey25519").addCredentials(credentials) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("proposition").size())
        assertEquals(1, result.path("proposition").findValues("publicKey").size())
        val node = result.get("proposition").get("publicKey")
        if (node == null)
          fail("Result serialization failed")

        assertTrue(node.isTextual)
      }
      // secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      sidechainApiMockConfiguration.setShould_nodeViewHolder_GenerateSecret_reply(false)
      Post(basePath + "createPrivateKey25519").addCredentials(credentials) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretNotAdded("", JOptional.empty()).code)
      }
    }

    "reply at /allPublicKeys" in {
      Post(basePath + "allPublicKeys").addCredentials(credentials) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("propositions").size())
        val node = result.get("propositions")
        if (node == null) {
          fail("Result serialization failed")
        }
        assertTrue(node.isArray)
        assertEquals(2, node.findValues("publicKey").size())
      }
      Post(basePath + "allPublicKeys")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqAllPropositions(Some("proptype")))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.InternalServerError.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
    }

    "reply at /importSecret" in {
      // private25519 secret is added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      Post(basePath + "importSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqImportSecret("002b64a179846da0b13ed5b4354dbdeb85a500c60ccb12c01a0fded2bd5d8b58e58bb8302e2b46763c830099c6fd862da0774a7b8f1323db5bbd96d3652176e485"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.findValues("proposition").size())
        assertEquals(1, result.path("proposition").findValues("publicKey").size())
        val node = result.get("proposition").get("publicKey")
        if (node == null)
          fail("Result serialization failed")

        assertTrue(node.isTextual)
      }
      //private25519 secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "importSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqImportSecret("002b64a179846da0b13ed5b4354dbdeb85a500c60ccb12c01a0fded2bd5d8b58e58bb8302e2b46763c830099c6fd862da0774a7b8f1323db5bbd96d3652176e485"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretAlreadyPresent("", JOptional.empty()).code)
      }
      // vrf secret is added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      Post(basePath + "importSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqImportSecret("03ff697dccef0a296a9a6682cd97a10056580f7447a2ff3e9b8609f35757b4661a076a9191a89fee51439600b0455db357a9899694d1cdad6a3c71bf65e6cce53280"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("proposition").size())
        assertEquals(1, result.path("proposition").findValues("publicKey").size())
        val node = result.get("proposition").get("publicKey")
        if (node == null)
          fail("Result serialization failed")

        assertTrue(node.isTextual)
      }
      //vrf secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "importSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqImportSecret("03ff697dccef0a296a9a6682cd97a10056580f7447a2ff3e9b8609f35757b4661a076a9191a89fee51439600b0455db357a9899694d1cdad6a3c71bf65e6cce53280"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretAlreadyPresent("", JOptional.empty()).code)
      }
      // schnorr secret is added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      Post(basePath + "importSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqImportSecret("04a785e48940b0e4653d67f7b347def796a8fc601f583c8dd238c13c93de127f35effcc974d518c9ca00bbbde69041141b5901cd325d237a1f3bf489ff804e712400"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")

        assertEquals(1, result.findValues("proposition").size())
        assertEquals(1, result.path("proposition").findValues("publicKey").size())
        val node = result.get("proposition").get("publicKey")
        if (node == null)
          fail("Result serialization failed")

        assertTrue(node.isTextual)
      }
      //schnorr secret is not added
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(false)
      Post(basePath + "importSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqImportSecret("04a785e48940b0e4653d67f7b347def796a8fc601f583c8dd238c13c93de127f35effcc974d518c9ca00bbbde69041141b5901cd325d237a1f3bf489ff804e712400"))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorSecretAlreadyPresent("", JOptional.empty()).code)
      }
    }

    "reply at /exportSecret" in {
      // private25519 secret is exported
      Post(basePath + "exportSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqExportSecret(BytesUtils.toHexString(utilMocks.listOfPropositions.head.pubKeyBytes())))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.findValues("privKey").size())
        val node = result.get("privKey")
        if (node == null)
          fail("Result serialization failed")

        assertEquals(node.asText(), BytesUtils.toHexString(sidechainSecretsCompanion.toBytes(utilMocks.listOfSecrets.head)))
        assertTrue(node.isTextual)
      }
      // private25519 secret not found in the wallet
      Post(basePath + "exportSecret")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqExportSecret(BytesUtils.toHexString(getPrivateKey25519.publicImage().bytes())))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        assertsOnSidechainErrorResponseSchema(entityAs[String], ErrorPropositionNotFound("", JOptional.empty()).code)
      }
    }

    "reply at /dumpSecrets" in {
      // dump all secrets
      Post(basePath + "dumpSecrets")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqDumpSecrets(dumpSecretsFilePath))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.findValues("status").size())
        val node = result.get("status")
        if (node == null)
          fail("Result serialization failed")
        assertTrue(node.isTextual)

        val dumpFile = new File(dumpSecretsFilePath)
        val reader = new Scanner(dumpFile)
        var secretNumber = 0
        while(reader.hasNext) {
          val line = reader.nextLine()
          if (!line.contains("#")) {
            val keyPair = line.split(" ")
            val secret = sidechainSecretsCompanion.parseBytesTry(BytesUtils.fromHexString(keyPair(0))).get
            assertTrue(utilMocks.listOfSecrets.contains(secret))
            assertTrue(utilMocks.listOfPropositions.contains(secret.publicImage()))
            secretNumber += 1
          }
        }
        assertEquals(secretNumber, utilMocks.listOfSecrets.size)
        reader.close()
        dumpFile.delete()
      }
    }

    "reply at /importSecrets" in {
      // import all secrets
      sidechainApiMockConfiguration.setShould_nodeViewHolder_LocallyGeneratedSecret_reply(true)
      createDumpSecretsFile(false, false)
      Post(basePath + "importSecrets")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqDumpSecrets(dumpSecretsFilePath))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.findValues("successfullyAdded").size())
        assertEquals(1, result.findValues("failedToAdd").size())
        assertEquals(1, result.findValues("summary").size())

        var node = result.get("successfullyAdded")
        if (node == null)
          fail("Result serialization failed")
        assertTrue(node.isInt)
        assertEquals(node.asInt(), 2)

        node = result.get("failedToAdd")
        if (node == null)
          fail("Result serialization failed")
        assertTrue(node.isInt)
        assertEquals(node.asInt(), 0)

        node = result.get("summary")
        if (node == null)
          fail("Result serialization failed")
        assertTrue(node.isArray)
        assertEquals(node.size(), 0)

        dumpFile.delete()
      }

      // failed because can't deserialize the secret
      createDumpSecretsFile(true, false)
      Post(basePath + "importSecrets")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqDumpSecrets(dumpSecretsFilePath))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.findValues("code").size())
        assertEquals(1, result.findValues("description").size())
        assertEquals(result.get("code").asText(), "0305")
        assertEquals(result.get("description").asText(), "Failed to parse the secret at line 4")
        dumpFile.delete()
      }

      // fail because a proposition doesn't match the corresponding secret
      createDumpSecretsFile(false, true)
      Post(basePath + "importSecrets")
        .addCredentials(credentials)
        .withEntity(
          SerializationUtil.serialize(ReqDumpSecrets(dumpSecretsFilePath))) ~> sidechainWalletApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val fr = mapper.readTree(entityAs[String])
        val result = fr.get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.findValues("code").size())
        assertEquals(1, result.findValues("description").size())
        assertEquals(result.get("code").asText(), "0304")
        assertEquals(result.get("description").asText(), "Public key doesn't match on line 4")
        dumpFile.delete()
      }

    }
  }
}