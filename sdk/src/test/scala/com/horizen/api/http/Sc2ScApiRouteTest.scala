package com.horizen.api.http

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import com.horizen.account.sc2sc.CrossChainMessageProcessorFixture
import com.horizen.api.http.Sc2scApiRouteRestScheme.{CrossChainMessageEle, ReqCreateRedeemMessage}
import com.horizen.sc2sc.Sc2scProver.ReceivableMessages.BuildRedeemMessage
import com.horizen.sc2sc.{CrossChainMessage, CrossChainMessageImpl, CrossChainProtocolVersion, CrossChainRedeemMessageImpl}
import com.horizen.serialization.SerializationUtil
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue}
import sparkz.core.NodeViewHolder.CurrentView
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.{Failure, Success}
import com.horizen.sc2sc.Sc2ScException


class Sc2ScApiRouteTest extends SidechainApiRouteTest with CrossChainMessageProcessorFixture{
  override val basePath = "/sc2sc/"
  type NodeView = CurrentView[Any, Any, Any, Any]
  var   mockSc2scProver = getMockSc2ScProver()
  val sc2scApiRoute: Route = Sc2scApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolder.ref, mockSc2scProver).route

  var simulateEnError = false

  val testCrossChainMessage =    new CrossChainMessageEle(
    CrossChainProtocolVersion.VERSION_1,
    1 ,
    BytesUtils.toHexString(getRandomBytes(14)),
    BytesUtils.toHexString(getRandomBytes(14)),
    BytesUtils.toHexString(getRandomBytes(14)),
    BytesUtils.toHexString(getRandomBytes(14)),
    BytesUtils.toHexString("testPayload".getBytes))

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sc2scApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sc2scApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "createRedeemMessage").withEntity("maybe_a_json") ~> sc2scApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
    }

    "reply at /createRedeemMessage" in {
      Post(basePath + "createRedeemMessage").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(
          ReqCreateRedeemMessage(testCrossChainMessage)
          )) ~> sc2scApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("redeemMessage").isObject)
        assertEquals(6, result.get("redeemMessage").elements().asScala.length)
        assertTrue(result.get("redeemMessage").get("proof").isTextual)
        assertTrue(result.get("redeemMessage").get("proof").asText().equals(BytesUtils.toHexString(redeemMesssage.getProof)))
      }
    }

    "reply with error at /createRedeemMessage" in {
      simulateEnError = true
      Post(basePath + "createRedeemMessage").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(
          ReqCreateRedeemMessage(testCrossChainMessage)
        )) ~> sc2scApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val result = mapper.readTree(entityAs[String]).get("error")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(3, result.elements().asScala.length)
        assertTrue(result.get("description").isTextual)
        assertTrue(result.get("description").asText().equals("Failed to create redeem message"))
        assertTrue(result.get("detail").isTextual)
        assertTrue(result.get("detail").asText().equals(anErrorDetail))

      }
    }
  }

  def getRandomBytes(num: Int) = {
    val bytes = new Array[Byte](num)
    scala.util.Random.nextBytes(bytes)
    bytes
  }

  def getMockSc2ScProver() = {
    val mockProver = TestProbe()
    mockProver.setAutoPilot(new testkit.TestActor.AutoPilot {
      override def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
        msg match {
          case BuildRedeemMessage(message: CrossChainMessage) =>
            simulateEnError match {
              case true =>  sender ! Failure(new Sc2ScException(anErrorDetail))
              case false =>  sender ! Success(redeemMesssage)
            }

        }
        TestActor.KeepRunning
      }
    })
    mockProver.ref
  }

  var anErrorDetail ="Dummy error detail"

  var redeemMesssage = new CrossChainRedeemMessageImpl(
    new CrossChainMessageImpl(
      CrossChainProtocolVersion.VERSION_1,
      1,
      getRandomBytes(14),
      getRandomBytes(14),
      getRandomBytes(14),
      getRandomBytes(14),
      getRandomBytes(14)
    ),
    getRandomBytes(14), getRandomBytes(14), getRandomBytes(14), getRandomBytes(14), getRandomBytes(14)
  )
}