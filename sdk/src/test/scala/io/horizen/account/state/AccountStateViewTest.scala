package io.horizen.account.state

import io.horizen.account.AccountFixture
import io.horizen.account.fixtures.AccountCrossChainMessageFixture
import io.horizen.account.sc2sc.{AbstractCrossChainMessageProcessor, CrossChainMessageProvider}
import io.horizen.account.storage.AccountStateMetadataStorageView
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.evm.{Address, StateDB}
import io.horizen.fixtures.StoreFixture
import io.horizen.params.NetworkParams
import io.horizen.proposition.MCPublicKeyHashProposition
import io.horizen.sc2sc.CrossChainMessage
import io.horizen.utils.WithdrawalEpochUtils.MaxWithdrawalReqsNumPerEpoch
import io.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256

class AccountStateViewTest extends JUnitSuite with MockitoSugar with MessageProcessorFixture with StoreFixture
  with AccountFixture with AccountCrossChainMessageFixture {

  var stateView: AccountStateView = _
  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val forgerStakeMessageProcessor: ForgerStakeMsgProcessor = ForgerStakeMsgProcessor(mockNetworkParams)
  val contractAddress: Address = forgerStakeMessageProcessor.contractAddress

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
      override lazy val crossChainMessageProviders: Seq[CrossChainMessageProvider] =
        Seq(mocCrossChainReqProvider1, mocCrossChainReqProvider2)
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
  def testCrossChainMessages(): Unit = {
    val epochNum = 102

    // No messages
    Mockito
      .when(stateView.crossChainMessageProviders(0).getCrossChainMessages(epochNum, stateView))
      .thenReturn(Seq())
    Mockito
      .when(stateView.crossChainMessageProviders(1).getCrossChainMessages(epochNum, stateView))
      .thenReturn(Seq())

    var res = stateView.getCrossChainMessages(epochNum)
    assertTrue("The list of crosschain messages is not empty", res.isEmpty)

    Mockito
      .when(mockNetworkParams.sidechainId).thenReturn("f3281225c13d6e6c79befd1781daaaf5".getBytes)

    // With some cross chain messages from different providers
    var fakeMessages: List[CrossChainMessage] = List()
    val senderSidechain = BytesUtils.fromHexString("7a03386bd56e577d5b99a40e61278d35ef455bd67f6ccc2825d9c1e834ddb623")
    (0 until 3).foreach(index => {
      // TODO: here bad sidechainId
      fakeMessages = fakeMessages :+ AbstractCrossChainMessageProcessor.buildCrossChainMessageFromAccount(getRandomAccountCrossMessage(index), senderSidechain)
    })

    Mockito
      .when(stateView.crossChainMessageProviders(0).getCrossChainMessages(epochNum, stateView))
      .thenReturn(Seq(fakeMessages(0), fakeMessages(1)))
    Mockito
      .when(stateView.crossChainMessageProviders(1).getCrossChainMessages(epochNum, stateView))
      .thenReturn(Seq(fakeMessages(2)))

    res = stateView.getCrossChainMessages(epochNum)

    assertEquals("Wrong list of crosschain messages size", 3, res.size)
    (0 until 3).foreach(index => {
      val wr: CrossChainMessage = res(index)
      assertEquals("wrong address", fakeMessages(index).getMessageType, wr.getMessageType)
      assertEquals("wrong payload", fakeMessages(index).getPayload, wr.getPayload)
    })

    val messageHash = res(1).getCrossChainMessageHash
    Mockito
      .when(stateView.crossChainMessageProviders(0).getCrossChainMessageHashEpoch(messageHash, stateView))
      .thenReturn(Some(epochNum))

    assertTrue("Crosschain message hash not found", stateView.getCrossChainMessageHashEpoch(messageHash).isDefined)
    assertEquals("Crosschain message hash epoch incorrect", epochNum, stateView.getCrossChainMessageHashEpoch(messageHash).get)

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
