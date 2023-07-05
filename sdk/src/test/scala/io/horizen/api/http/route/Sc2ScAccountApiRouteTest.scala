package io.horizen.api.http.route

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, MethodRejection, Route}
import akka.testkit
import akka.testkit.{TestActor, TestProbe}
import io.horizen.account.sc2sc.{AccountCrossChainRedeemMessage, CrossChainMessageProcessorFixture}
import io.horizen.account.storage.AccountStateMetadataStorage
import io.horizen.api.http.Sc2ScAccountApiRouteRestScheme.{AccountCrossChainMessageEle, ReqCreateAccountRedeemMessage}
import io.horizen.api.http.Sc2scAccountApiRoute
import io.horizen.api.http.Sc2scApiRouteRestScheme.{CrossChainMessageEle, ReqCreateRedeemMessage}
import io.horizen.consensus.ConsensusEpochNumber
import io.horizen.fork.{ForkManagerUtil, Sc2ScOptionalForkConfigurator}
import io.horizen.json.SerializationUtil
import io.horizen.sc2sc.Sc2scProver.ReceivableMessages.BuildRedeemMessage
import io.horizen.sc2sc.{CrossChainMessage, CrossChainProtocolVersion, CrossChainRedeemMessage, CrossChainRedeemMessageImpl, Sc2ScException}
import io.horizen.utils.BytesUtils
import io.horizen.utxo.storage.SidechainStateStorage
import org.junit.Assert.{assertEquals, assertTrue}
import org.mockito.Mockito
import sparkz.core.NodeViewHolder.CurrentView

import scala.jdk.CollectionConverters.asScalaIteratorConverter
import scala.util.{Failure, Success}

class Sc2ScAccountApiRouteTest extends SidechainApiRouteTest with CrossChainMessageProcessorFixture {
  override val basePath = "/sc2sc/"
  type NodeView = CurrentView[Any, Any, Any, Any]
  var mockSc2scProver: ActorRef = getMockSc2ScProver()
  val mockMetadataStorage: AccountStateMetadataStorage = mock[AccountStateMetadataStorage]
  val sc2scApiRoute: Route = Sc2scAccountApiRoute(mockedRESTSettings, mockedSidechainNodeViewHolder.ref, mockSc2scProver, mockMetadataStorage).route
  val scId: String = BytesUtils.toHexString(getRandomBytes(32))
  var simulateEnError = false

  ForkManagerUtil.initializeForkManager(new Sc2ScOptionalForkConfigurator, "regtest")
  Mockito.when(mockMetadataStorage.getConsensusEpochNumber).thenReturn(Some(ConsensusEpochNumber(5)))

  val testCrossChainMessage: AccountCrossChainMessageEle = AccountCrossChainMessageEle(
    1,
    BytesUtils.toHexString(getRandomBytes(32)),
    BytesUtils.toHexString(getRandomBytes(32)),
    BytesUtils.toHexString(getRandomBytes(32)),
    BytesUtils.toHexString(getRandomBytes(32))
  )

  "The Api should to" should {

    "reject and reply with http error" in {
      Get(basePath) ~> sc2scApiRoute ~> check {
        rejection shouldBe MethodRejection(HttpMethods.POST)
      }
      Get(basePath) ~> Route.seal(sc2scApiRoute) ~> check {
        status.intValue() shouldBe StatusCodes.MethodNotAllowed.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
      }
      Post(basePath + "createAccountRedeemMessage").withEntity("maybe_a_json") ~> sc2scApiRoute ~> check {
        rejection.getClass.getCanonicalName.contains(MalformedRequestContentRejection.getClass.getCanonicalName.toString)
      }
    }

    "reply at /createAccountRedeemMessage" in {
      Post(basePath + "createAccountRedeemMessage").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(
          ReqCreateAccountRedeemMessage(testCrossChainMessage, scId)
        )) ~> sc2scApiRoute ~> check {
        status.intValue() shouldBe StatusCodes.OK.intValue
        responseEntity.getContentType() shouldEqual ContentTypes.`application/json`
        val node = mapper.readTree(entityAs[String])
        val result = node.get("result")
        if (result == null)
          fail("Serialization failed for object SidechainApiResponseBody")
        assertEquals(1, result.elements().asScala.length)
        assertTrue(result.get("redeemMessage").isObject)
        assertEquals(6, result.get("redeemMessage").elements().asScala.length)
        assertTrue(result.get("redeemMessage").get("proof").isTextual)
        assertTrue(result.get("redeemMessage").get("proof").asText().equals(BytesUtils.toHexString(redeemMessage.getProof)))
      }
    }

    "reply with error at /createAccountRedeemMessage" in {
      simulateEnError = true
      Post(basePath + "createAccountRedeemMessage").addCredentials(credentials)
        .withEntity(SerializationUtil.serialize(
          ReqCreateAccountRedeemMessage(testCrossChainMessage, scId)
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
    mockProver.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case BuildRedeemMessage(_: CrossChainMessage) =>
          simulateEnError match {
            case true => sender ! Failure(new Sc2ScException(anErrorDetail))
            case false => sender ! Success(redeemMessage)
          }

      }
      TestActor.KeepRunning
    })
    mockProver.ref
  }

  var anErrorDetail = "Dummy error detail"

  var redeemMessage = new CrossChainRedeemMessageImpl(
    new CrossChainMessage(
      CrossChainProtocolVersion.VERSION_1,
      1,
      getRandomBytes(32),
      getRandomBytes(32),
      getRandomBytes(32),
      getRandomBytes(32),
      getRandomBytes(32)
    ),
    getRandomBytes(32), getRandomBytes(32), getRandomBytes(32), getRandomBytes(32), getRandomBytes(14)
  )
}