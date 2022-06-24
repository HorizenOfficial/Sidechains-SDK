package com.horizen.account.state

import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.StateDB
import com.horizen.proposition.MCPublicKeyHashProposition
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import scala.util.Random


class AccountStateViewTest
  extends JUnitSuite
    with MockitoSugar {

  var stateView: AccountStateView = _

  @Before
  def setUp(): Unit = {

    val mockWithdrawalReqProvider = mock[WithdrawalRequestProvider]
    val messageProcessors: Seq[MessageProcessor] = Seq()
    val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb: StateDB = mock[StateDB]
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors) {
      override lazy val withdrawalReqProvider: WithdrawalRequestProvider = mockWithdrawalReqProvider
    }

  }

  @Test
  def testWithdrawalReqProviderFieldInitialization(): Unit = {

    val messageProcessors: Seq[MessageProcessor] = Seq(mock[MessageProcessor], mock[MessageProcessor], WithdrawalMsgProcessor, mock[MessageProcessor])
    val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb: StateDB = mock[StateDB]
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)

    assertEquals("Wrong withdrawalReqProvider", WithdrawalMsgProcessor, stateView.withdrawalReqProvider)

  }

  @Test
  def testGetListOfWithdrawalReqs(): Unit = {
    val epochNum = 102

    // No withdrawal requests

    Mockito.when(stateView.withdrawalReqProvider.getListOfWithdrawalReqRecords(epochNum, stateView)).thenReturn(Seq())

    var res = stateView.withdrawalRequests(epochNum)

    assertTrue("The list of withdrawal requests is not empty", res.isEmpty)


    // With 3900 withdrawal requests
    val maxNumOfWithdrawalReqs = WithdrawalMsgProcessor.MaxWithdrawalReqsNumPerEpoch

    val destAddress = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    val listOfWR = (1 to maxNumOfWithdrawalReqs).map(index => {
      WithdrawalRequest(destAddress, ZenWeiConverter.convertZenniesToWei(index))
    }
    )
    Mockito.when(stateView.withdrawalReqProvider.getListOfWithdrawalReqRecords(epochNum, stateView)).thenReturn(listOfWR)

    res = stateView.withdrawalRequests(epochNum)

    assertEquals("Wrong list of withdrawal requests size", maxNumOfWithdrawalReqs, res.size)
    (0 until maxNumOfWithdrawalReqs).foreach(index => {
      val wr = res(index)
      assertEquals("wrong address", destAddress, wr.proposition)
      assertEquals("wrong amount", index + 1, wr.value)
    })

  }


}
