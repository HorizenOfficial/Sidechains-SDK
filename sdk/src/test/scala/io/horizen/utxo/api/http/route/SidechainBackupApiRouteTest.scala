package io.horizen.utxo.api.http.route

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import io.horizen.api.http.route.SidechainApiRouteTest
import io.horizen.json.SerializationUtil
import io.horizen.utils.BytesUtils
import io.horizen.utxo.api.http.route.SidechainBackupRestScheme.ReqGetInitialBoxes
import org.junit.Assert.{assertEquals, assertTrue}

import scala.collection.JavaConverters.asScalaIteratorConverter

class SidechainBackupApiRouteTest extends SidechainApiRouteTest{
  override val basePath = "/backup/"

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sidechainBackupApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sidechainBackupApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "getRestoredBoxes").withEntity("maybe_a_json") ~> sidechainBackupApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      //Test with more than max numberOfElements.
      Post(basePath + "getRestoredBoxes").withEntity("{\"numberOfElements\": 101}") ~> sidechainBackupApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
      //Test with negative numberOfElements.
      Post(basePath + "getRestoredBoxes").withEntity("{\"numberOfElements\": -1}") ~> sidechainBackupApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
    }
  }
  "reply at /getRestoredBoxes" in {
    //Test with invalid "lastBoxId".
    Post(basePath + "getRestoredBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(3, Some("invalid_json")))) ~> sidechainBackupApiRoute ~> check {
      status.intValue() shouldBe StatusCodes.OK.intValue
      responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      val result = mapper.readTree(entityAs[String]).get("error")
      if (result == null)
        fail("Serialization failed for object SidechainApiResponseBody")

      val errorCode = result.get("code")
      if (errorCode == null)
        fail("Result serialization failed")
      assertEquals(errorCode.asText(), "0802")
    }
    //Test with no "lastBoxId". It should return all mocked boxes
    Post(basePath + "getRestoredBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(3, None))) ~> sidechainBackupApiRoute ~> check {
      status.intValue() shouldBe StatusCodes.OK.intValue
      responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      val result = mapper.readTree(entityAs[String]).get("result")
      if (result == null)
        fail("Serialization failed for object SidechainApiResponseBody")
      assertEquals(1, result.findValues("boxes").size())

      val boxes = result.get("boxes")
      if (boxes == null)
        fail("Result serialization failed")

      assertTrue(boxes.isArray)
      assertEquals(storedBoxList.size, boxes.elements().asScala.length)

      mockStorageIterator
    }
    //Test with empty "lastBoxId". It should return all mocked boxes
    Post(basePath + "getRestoredBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(3, Some("")))) ~> sidechainBackupApiRoute ~> check {
      status.intValue() shouldBe StatusCodes.OK.intValue
      responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      val result = mapper.readTree(entityAs[String]).get("result")
      if (result == null)
        fail("Serialization failed for object SidechainApiResponseBody")
      assertEquals(1, result.findValues("boxes").size())

      val boxes = result.get("boxes")
      if (boxes == null)
        fail("Result serialization failed")

      assertTrue(boxes.isArray)
      assertEquals(storedBoxList.size, boxes.elements().asScala.length)

      mockStorageIterator
    }
    //Test with "lastBoxId"=mockedBoxes.head. It should skip the first box of the mockedBox list.
    //Also test that we return less boxes than "numberOfElement" requested in case of no more boxes.
    Post(basePath + "getRestoredBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(3, Some(BytesUtils.toHexString(boxList.head.id()))))) ~> sidechainBackupApiRoute ~> check {
      status.intValue() shouldBe StatusCodes.OK.intValue
      responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      val result = mapper.readTree(entityAs[String]).get("result")
      if (result == null)
        fail("Serialization failed for object SidechainApiResponseBody")
      assertEquals(1, result.findValues("boxes").size())

      val boxes = result.get("boxes")
      if (boxes == null)
        fail("Result serialization failed")

      assertTrue(boxes.isArray)
      assertEquals(storedBoxList.size -1, boxes.elements().asScala.length)

      mockStorageIterator
    }
  }
}