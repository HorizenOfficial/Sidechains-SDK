package io.horizen.utxo.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import io.horizen.consensus.ConsensusParamsUtil
import io.horizen.fork.{ConsensusParamsFork, ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.utils.TimeToEpochUtils
import io.horizen.utxo.fixtures.SidechainNodeViewHolderFixture
import io.horizen.utxo.node.SidechainNodeView
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.featurespec.AnyFeatureSpecLike
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

import java.util.concurrent.TimeUnit
import scala.collection.Seq
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps


@RunWith(classOf[JUnitRunner])
class SidechainNodeViewHolderActorTest extends Suites(
  new SidechainNodeViewHolderActorTest1,
  new SidechainNodeViewHolderActorTest2
) {}

@RunWith(classOf[JUnitRunner])
class SidechainNodeViewHolderActorTest1
  extends TestKit(ActorSystem("testsystem"))
  with AnyFunSuiteLike
  with BeforeAndAfterAll
  with SidechainNodeViewHolderFixture
{
  ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator, "regtest")
  ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
    (0, ConsensusParamsFork.DefaultConsensusParamsFork)
  ))
  ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(TimeToEpochUtils.virtualGenesisBlockTimeStamp(params)))

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
}

@RunWith(classOf[JUnitRunner])
class SidechainNodeViewHolderActorTest2
  extends TestKit(ActorSystem("testSystem"))
  with AnyFeatureSpecLike
  with BeforeAndAfterAll
  with Matchers
  with SidechainNodeViewHolderFixture
{
  ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator, "regtest")
  ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
    (0, ConsensusParamsFork.DefaultConsensusParamsFork)
  ))
  ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(TimeToEpochUtils.virtualGenesisBlockTimeStamp(params)))

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  override def afterAll: Unit = {
    //info("Actor system is shutting down...")
    TestKit.shutdownActorSystem(system)
  }

  Feature("Actor1") {
    Scenario("Scenario 1"){
      system should not be(null)

      def f(v: SidechainNodeView) = v
      val sidechainNodeViewHolderRef: ActorRef = getSidechainNodeViewHolderRef
      val nodeView = (sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
        .mapTo[SidechainNodeView]

      Await.result(nodeView, 5 seconds) should not be(null)

    }
  }
}
