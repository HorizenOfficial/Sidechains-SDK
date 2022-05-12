package com.horizen.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import com.horizen.api.http.SidechainBackupRestScheme.ReqGetInitialBoxes
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
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
      Post(basePath + "getInitialBoxes").withEntity("maybe_a_json") ~> sidechainBackupApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
    }
  }
  "reply at /getInitialBoxes" in {
    //Test with invalid "lastBoxId".
    Post(basePath + "getInitialBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(7, Some("invalid_json")))) ~> sidechainBackupApiRoute ~> check {
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
    Post(basePath + "getInitialBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(3, None))) ~> sidechainBackupApiRoute ~> check {
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

      val startedBoxId = result.get("startingBoxId")
      if (startedBoxId != null)
        fail("There shouldn't be a startingBoxId")
      mockStorageIterator
    }
    //Test with empty "lastBoxId". It should return all mocked boxes
    Post(basePath + "getInitialBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(3, Some("")))) ~> sidechainBackupApiRoute ~> check {
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

      val startedBoxId = result.get("startingBoxId")
      if (startedBoxId == null)
        fail("There should be a startingBoxId")
      assertEquals(startedBoxId.asText(), "")
      mockStorageIterator
    }
    //Test with "lastBoxId"=mockedBoxes.head. It should skip the first box of the mockedBox list.
    //Also test that we return less boxes than "numberOfElement" requested in case of no more boxes.
    Post(basePath + "getInitialBoxes").withEntity(SerializationUtil.serialize(ReqGetInitialBoxes(3, Some(BytesUtils.toHexString(boxList.head.id()))))) ~> sidechainBackupApiRoute ~> check {
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

      val startedBoxId = result.get("startingBoxId")
      if (startedBoxId == null)
        fail("There should be a startingBoxId")
      assertEquals(startedBoxId.asText(), BytesUtils.toHexString(boxList.head.id()))
      mockStorageIterator
    }
  }
}
