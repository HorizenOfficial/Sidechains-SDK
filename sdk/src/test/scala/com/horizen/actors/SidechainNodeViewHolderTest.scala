package com.horizen.actors

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.fixtures.SidechainNodeViewHolderFixture
import com.horizen.node.SidechainNodeView
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent._
import scala.concurrent.duration._
import org.scalatest._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class SidechainNodeViewHolderTest extends Suites(
  new SidechainNodeViewHolderTest1,
  new SidechainNodeViewHolderTest2
)

@RunWith(classOf[JUnitRunner])
class SidechainNodeViewHolderTest1
  extends TestKit(ActorSystem("testsystem"))
  with FunSuiteLike
  with BeforeAndAfterAll
  with SidechainNodeViewHolderFixture
{

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  override def afterAll: Unit = {
    //info("Actor system is shutting down...")
    TestKit.shutdownActorSystem(system)
  }

  test ("Test1") {
    def f(v: SidechainNodeView) = v
    val sidechainNodeViewHolderRef: ActorRef = getSidechainNodeViewHolderRef
    val nodeView = (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
      .mapTo[SidechainNodeView]

    assert(Await.result(nodeView, 5 seconds) != null)
  }

  test("Test2") {
  }

}

@RunWith(classOf[JUnitRunner])
class SidechainNodeViewHolderTest2
  extends TestKit(ActorSystem("testSystem"))
  with FeatureSpecLike
  with BeforeAndAfterAll
  with Matchers
  with SidechainNodeViewHolderFixture
{

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  override def afterAll: Unit = {
    //info("Actor system is shutting down...")
    TestKit.shutdownActorSystem(system)
  }

  feature("Actor1") {
    scenario("Scenario 1"){
      system should not be(null)

      def f(v: SidechainNodeView) = v
      val sidechainNodeViewHolderRef: ActorRef = getSidechainNodeViewHolderRef
      val nodeView = (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
        .mapTo[SidechainNodeView]

      Await.result(nodeView, 5 seconds) should not be(null)

    }
  }
}
