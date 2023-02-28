package com.horizen.utxo.forge

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import com.horizen.utxo.companion.SidechainTransactionsCompanion
import com.horizen.forge.AbstractForger.ReceivableMessages.StartForging
import com.horizen.forge.MainchainSynchronizer
import com.horizen.params.NetworkParams
import com.horizen.utxo.block.SidechainBlock
import com.horizen.{SidechainSettings, WebSocketSettings}
import org.junit.Test
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import sparkz.core.settings.{RESTApiSettings, SparkzSettings}
import sparkz.core.utils.NetworkTimeProvider

import scala.concurrent.duration.DurationInt

class ForgerTest extends JUnitSuite with Matchers {

  implicit val system: ActorSystem = ActorSystem()

  @Test
  def testForgingScheduleAtTheBeginningOfNewSlot(): Unit = {
    /*
    Intent here is to start forging blocks 1 second before the next slot,
    with consensusSecondsInSlot being 2 seconds
    Math is TimeToEpochUtils is quite entangled, so let me explain the values below:
    epochInSeconds = consensusSlotsInEpoch * consensusSecondsInSlot = 1 * 2 = 2
    virtualGenesisBlockTimeStamp = sidechainGenesisBlockTimestamp - epochInSeconds + consensusSecondsInSlot
      = 11 - 2 + 2 = 11
    secondsElapsedInSlot = (timestamp - virtualGenesisBlockTimeStamp) % consensusSecondsInSlot = (20 - 11) % 2 = 1
    secondsRemainingInSlot = consensusSecondsInSlot - secondsElapsedInSlot = 2 - 1 = 1
     */

    val timeProvider = mock[NetworkTimeProvider]
    when(timeProvider.time()).thenReturn(20000)
    val params = mock[NetworkParams]
    when(params.consensusSlotsInEpoch).thenReturn(1)
    when(params.consensusSecondsInSlot).thenReturn(2)
    when(params.sidechainGenesisBlockTimestamp).thenReturn(11)

    val (forger, viewHolder) = prepareTestData(params, timeProvider)

    forger ! StartForging
    viewHolder.expectMsgType[LocallyGeneratedModifier[SidechainBlock]] //first right away
    viewHolder.expectNoMessage(900.millis) //then ~1s pause
    viewHolder.expectMsgType[LocallyGeneratedModifier[SidechainBlock]](1100.millis) //next in a second
    viewHolder.expectNoMessage(1900.millis) //then ~2s pause
    viewHolder.expectMsgType[LocallyGeneratedModifier[SidechainBlock]](2100.millis) //next in 2 seconds
  }

  def prepareTestData(params: NetworkParams, timeProvider: NetworkTimeProvider): (ActorRef, TestProbe) = {
    val settings = mock[SidechainSettings]
    val webSocketSettings = mock[WebSocketSettings]
    val sparkzSettings = mock[SparkzSettings]
    val restApiSettings = mock[RESTApiSettings]
    when(settings.websocket).thenReturn(webSocketSettings)
    when(webSocketSettings.allowNoConnectionInRegtest).thenReturn(true)
    when(settings.sparkzSettings).thenReturn(sparkzSettings)
    when(sparkzSettings.restApi).thenReturn(restApiSettings)
    when(restApiSettings.timeout).thenReturn(1.seconds)
    val viewHolder = TestProbe()
    val mainchainSynchronizer = mock[MainchainSynchronizer]
    val companion = mock[SidechainTransactionsCompanion]

    val forgeMessageBuilder: ForgeMessageBuilder = new ForgeMessageBuilder(mainchainSynchronizer, companion, params, settings.websocket.allowNoConnectionInRegtest)

    class ForgerUnderTest(settings: SidechainSettings,
                     viewHolderRef: ActorRef,
                     mainchainSynchronizer: MainchainSynchronizer,
                     companion: SidechainTransactionsCompanion,
                     timeProvider: NetworkTimeProvider,
                     params: NetworkParams) extends Forger(settings, viewHolderRef, forgeMessageBuilder, timeProvider, params) {
      override protected def tryToCreateBlockNow(): Unit = {
        viewHolderRef ! LocallyGeneratedModifier[SidechainBlock](null)
      }
    }

    val forgerUnderTest = system.actorOf(Props(new ForgerUnderTest(settings, viewHolder.ref, mainchainSynchronizer, companion, timeProvider, params)))

    (forgerUnderTest, viewHolder)
  }
}
