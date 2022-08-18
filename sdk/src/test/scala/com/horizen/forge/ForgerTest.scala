package com.horizen.forge

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.horizen.{SidechainSettings, WebSocketSettings}
import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.forge.Forger.ReceivableMessages.StartForging
import com.horizen.params.NetworkParams
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import scorex.core.settings.{RESTApiSettings, ScorexSettings}
import scorex.core.utils.NetworkTimeProvider

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class ForgerTest extends JUnitSuite with Matchers {

  implicit val system: ActorSystem = ActorSystem()

  @Test
  def testForgingScheduleAtTheBeginningOfNewSlot(): Unit = {
    /*
    Intent here is to start forging blocks 1 second before the next slot,
    with consensusSecondsInSlot being 5 seconds
    Math is TimeToEpochUtils is quite entangled, so let me explain the values below:
    epochInSeconds = consensusSlotsInEpoch * consensusSecondsInSlot = 1 * 5 = 5
    virtualGenesisBlockTimeStamp = sidechainGenesisBlockTimestamp - epochInSeconds + consensusSecondsInSlot
      = 11 - 5 + 5 = 11
    secondsElapsedInSlot = (timestamp - virtualGenesisBlockTimeStamp) % consensusSecondsInSlot = (20 - 11) % 5 = 4
    secondsRemainingInSlot = consensusSecondsInSlot - secondsElapsedInSlot = 5 - 4 = 1
     */

    val timeProvider = mock[NetworkTimeProvider]
    when(timeProvider.time()).thenReturn(20000)
    val params = mock[NetworkParams]
    when(params.consensusSlotsInEpoch).thenReturn(1)
    when(params.consensusSecondsInSlot).thenReturn(5)
    when(params.sidechainGenesisBlockTimestamp).thenReturn(11)

    val (forger, viewHolder) = prepareTestData(params, timeProvider)

    forger ! StartForging
    viewHolder.expectMsgType[LocallyGeneratedModifier[SidechainBlock]] //first right away
    viewHolder.expectNoMessage(900.millis) //then ~1s pause
    viewHolder.expectMsgType[LocallyGeneratedModifier[SidechainBlock]](1100.millis) //next in a second
    viewHolder.expectNoMessage(4900.millis) //then ~10s pause
    viewHolder.expectMsgType[LocallyGeneratedModifier[SidechainBlock]](5100.millis) //next in 10 seconds
  }

  def prepareTestData(params: NetworkParams, timeProvider: NetworkTimeProvider): (ActorRef, TestProbe) = {
    val settings = mock[SidechainSettings]
    val webSocketSettings = mock[WebSocketSettings]
    val scorexSettings = mock[ScorexSettings]
    val restApiSettings = mock[RESTApiSettings]
    when(settings.websocket).thenReturn(webSocketSettings)
    when(webSocketSettings.allowNoConnectionInRegtest).thenReturn(true)
    when(settings.scorexSettings).thenReturn(scorexSettings)
    when(scorexSettings.restApi).thenReturn(restApiSettings)
    when(restApiSettings.timeout).thenReturn(1.seconds)
    val viewHolder = TestProbe()
    val mainchainSynchronizer = mock[MainchainSynchronizer]
    val companion = mock[SidechainTransactionsCompanion]

    class TestForget(settings: SidechainSettings,
                     viewHolderRef: ActorRef,
                     mainchainSynchronizer: MainchainSynchronizer,
                     companion: SidechainTransactionsCompanion,
                     timeProvider: NetworkTimeProvider,
                     params: NetworkParams) extends Forger(settings, viewHolderRef, mainchainSynchronizer, companion, timeProvider, params) {
      override protected def tryToCreateBlockNow(): Unit = {
        viewHolderRef ! LocallyGeneratedModifier[SidechainBlock](null)
      }
    }

    val testForger = system.actorOf(Props(new TestForget(settings, viewHolder.ref, mainchainSynchronizer, companion, timeProvider, params)))
    (testForger, viewHolder)
  }
}
