package com.horizen.account.state

import com.horizen.account.AccountFixture
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.{Address, StateDB}
import com.horizen.fixtures.StoreFixture
import com.horizen.params.NetworkParams
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.ByteArrayWrapper
import com.horizen.utils.WithdrawalEpochUtils.MaxWithdrawalReqsNumPerEpoch
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256

class AccountStateViewTest extends JUnitSuite with MockitoSugar with MessageProcessorFixture with StoreFixture
      with AccountFixture {

  var stateView: AccountStateView = _
  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val forgerStakeMessageProcessor: ForgerStakeMsgProcessor = ForgerStakeMsgProcessor(mockNetworkParams)
  val contractAddress: Address = forgerStakeMessageProcessor.contractAddress

  @Before
  def setUp(): Unit = {
    val mockWithdrawalReqProvider = mock[WithdrawalRequestProvider]
    val messageProcessors = Seq[MessageProcessor]()
    val metadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb = mock[StateDB]
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors) {
      override lazy val withdrawalReqProvider: WithdrawalRequestProvider =
        mockWithdrawalReqProvider
    }
  }

  @Test
  def testWithdrawalReqProviderFieldInitialization(): Unit = {
    val messageProcessors =
      Seq(mock[MessageProcessor], mock[MessageProcessor], WithdrawalMsgProcessor, mock[MessageProcessor])
    val metadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb = mock[StateDB]
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)

    assertEquals("Wrong withdrawalReqProvider", WithdrawalMsgProcessor, stateView.withdrawalReqProvider)
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

    val destAddress = new MCPublicKeyHashProposition(randomBytes(20))
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
  def testNullRecords(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>
      forgerStakeMessageProcessor.init(view)

      // getting a not existing key from state DB using RAW strategy gives an array of 32 bytes filled with 0, while
      // using CHUNK strategy gives an empty array instead.
      // If this behaviour changes, the codebase must change as well

      val notExistingKey1 = Keccak256.hash("NONE1")
      view.removeAccountStorage(contractAddress, notExistingKey1)
      val ret1 = view.getAccountStorage(contractAddress, notExistingKey1)
      assertEquals(new ByteArrayWrapper(new Array[Byte](32)), new ByteArrayWrapper(ret1))

      val notExistingKey2 = Keccak256.hash("NONE2")
      view.removeAccountStorageBytes(contractAddress, notExistingKey2)
      val ret2 = view.getAccountStorageBytes(contractAddress, notExistingKey2)
      assertEquals(new ByteArrayWrapper(new Array[Byte](0)), new ByteArrayWrapper(ret2))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }
}
