package com.horizen.account.state

import com.horizen.account.fixtures.AccountCrossChainMessageFixture
import com.horizen.account.sc2sc.{AbstractCrossChainMessageProcessor, AccountCrossChainMessage, CrossChainMessageProvider}
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.cryptolibprovider.{CryptoLibProvider}
import com.horizen.evm.StateDB
import com.horizen.params.MainNetParams
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.WithdrawalEpochUtils.MaxWithdrawalReqsNumPerEpoch
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import com.horizen.sc2sc.CrossChainMessage

import scala.util.Random

class AccountStateViewTest extends JUnitSuite
  with MockitoSugar
  with AccountCrossChainMessageFixture{

  var stateView: AccountStateView = _
  var params = MainNetParams()

  @Before
  def setUp(): Unit = {
    val mockWithdrawalReqProvider = mock[WithdrawalRequestProvider]
    val mocCrossChainReqProvider1 = mock[CrossChainMessageProvider]
    val mocCrossChainReqProvider2 = mock[CrossChainMessageProvider]
    val messageProcessors: Seq[MessageProcessor] = Seq()
    val metadataStorageView: AccountStateMetadataStorageView =
      mock[AccountStateMetadataStorageView]
    val stateDb: StateDB = mock[StateDB]
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors) {
      override lazy val withdrawalReqProvider: WithdrawalRequestProvider = mockWithdrawalReqProvider
      override lazy val crossChainMessageProviders = Seq(mocCrossChainReqProvider1, mocCrossChainReqProvider2)
    }
  }

  @Test
  def testWithdrawalReqProviderFieldInitialization(): Unit = {
    val messageProcessors: Seq[MessageProcessor] = Seq(
      mock[MessageProcessor],
      mock[MessageProcessor],
      WithdrawalMsgProcessor,
      mock[MessageProcessor])
    val metadataStorageView: AccountStateMetadataStorageView =
      mock[AccountStateMetadataStorageView]
    val stateDb: StateDB = mock[StateDB]
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)

    assertEquals(
      "Wrong withdrawalReqProvider",
      WithdrawalMsgProcessor,
      stateView.withdrawalReqProvider)
  }

  @Test
  def testGetListOfWithdrawalReqs(): Unit = {
    val epochNum = 102

    // No withdrawal requests
    Mockito
      .when(stateView.withdrawalReqProvider.getListOfWithdrawalReqRecords(epochNum, stateView))
      .thenReturn(Seq())

    var res = stateView.getWithdrawalRequests(epochNum)
    assertTrue("The list of withdrawal requests is not empty", res.isEmpty)

    // With 3999 withdrawal requests
    val maxNumOfWithdrawalReqs = MaxWithdrawalReqsNumPerEpoch

    val destAddress = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    val listOfWR = (1 to maxNumOfWithdrawalReqs).map(index => {
      WithdrawalRequest(destAddress, ZenWeiConverter.convertZenniesToWei(index))
    })
    Mockito
      .when(stateView.withdrawalReqProvider.getListOfWithdrawalReqRecords(epochNum, stateView))
      .thenReturn(listOfWR)

    res = stateView.getWithdrawalRequests(epochNum)

    assertEquals("Wrong list of withdrawal requests size", maxNumOfWithdrawalReqs, res.size)
    (0 until maxNumOfWithdrawalReqs).foreach(index => {
      val wr = res(index)
      assertEquals("wrong address", destAddress, wr.proposition)
      assertEquals("wrong amount", index + 1, wr.valueInZennies)
    })
  }

  @Test
  def testCrosschainMessages(): Unit = {
    val epochNum = 102

    // No messages
    Mockito
      .when(stateView.crossChainMessageProviders(0).getCrossChainMesssages(epochNum, stateView))
      .thenReturn(Seq())
    Mockito
      .when(stateView.crossChainMessageProviders(1).getCrossChainMesssages(epochNum, stateView))
      .thenReturn(Seq())

    var res = stateView.getCrossChainMessages(epochNum)
    assertTrue("The list of crosschain messages is not empty", res.isEmpty)

    // With some cross chain messages from different providers
    var fakeMessages : List[CrossChainMessage] = List()
    (0 until 3).foreach(index => {
      fakeMessages = fakeMessages :+  AbstractCrossChainMessageProcessor.buildCrosschainMessageFromAccount( getRandomAccountCrossMessage(index), params)
    })

    Mockito
      .when(stateView.crossChainMessageProviders(0).getCrossChainMesssages(epochNum, stateView))
      .thenReturn(Seq(fakeMessages(0), fakeMessages(1)))
    Mockito
      .when(stateView.crossChainMessageProviders(1).getCrossChainMesssages(epochNum, stateView))
      .thenReturn(Seq(fakeMessages(2)))

    res = stateView.getCrossChainMessages(epochNum)

    assertEquals("Wrong list of crosschain messages size", 3, res.size)
    (0 until 3).foreach(index => {
      val wr : CrossChainMessage = res(index)
      assertEquals("wrong address", fakeMessages(index).getMessageType, wr.getMessageType)
      assertEquals("wrong payload", fakeMessages(index).getPayload, wr.getPayload)
    })

    val messageHash =  CryptoLibProvider.sc2scCircuitFunctions.getCrossChainMessageHash(res(1))
    Mockito
      .when(stateView.crossChainMessageProviders(0).getCrossChainMessageHashEpoch(messageHash, stateView))
      .thenReturn(Some(epochNum))

    assertTrue("Crosschain message hash not found", stateView.getCrossChainMessageHashEpoch(messageHash).isDefined )
    assertEquals("Crosschain message hash epoch incorrect",  epochNum, stateView.getCrossChainMessageHashEpoch(messageHash).get)

  }
}