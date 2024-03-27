package io.horizen.account.state

import io.horizen.account.AccountFixture
import io.horizen.account.fixtures.ForgerAccountFixture.getPrivateKeySecp256k1
import io.horizen.account.fork.Version1_2_0Fork
import io.horizen.account.storage.AccountStateMetadataStorageView
import io.horizen.account.utils.WellKnownAddresses.FORGER_POOL_RECIPIENT_ADDRESS
import io.horizen.account.utils.ZenWeiConverter.MAX_MONEY_IN_WEI
import io.horizen.account.utils.{WellKnownAddresses, ZenWeiConverter}
import io.horizen.consensus.intToConsensusEpochNumber
import io.horizen.evm.{Address, StateDB}
import io.horizen.fixtures.StoreFixture
import io.horizen.fork.{ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch, SimpleForkConfigurator}
import io.horizen.params.NetworkParams
import io.horizen.proposition.MCPublicKeyHashProposition
import io.horizen.utils
import io.horizen.utils.ByteArrayWrapper
import io.horizen.utils.WithdrawalEpochUtils.MaxWithdrawalReqsNumPerEpoch
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito.when
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.util
import scala.jdk.CollectionConverters.seqAsJavaListConverter

class AccountStateViewTest extends JUnitSuite with MockitoSugar with MessageProcessorFixture with StoreFixture
      with AccountFixture {

  var stateView: AccountStateView = _
  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val forgerStakeMessageProcessor: ForgerStakeMsgProcessor = ForgerStakeMsgProcessor(mockNetworkParams)
  val contractAddress: Address = forgerStakeMessageProcessor.contractAddress

  @Before
  def setUp(): Unit = {
    val forkConfigurator = new SimpleForkConfigurator() {
      override def getOptionalSidechainForks: util.List[utils.Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = List(
        new utils.Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](
          SidechainForkConsensusEpoch(35, 35, 35),
          new Version1_2_0Fork(true)
        )
      ).asJava
    }
    ForkManagerUtil.initializeForkManager(forkConfigurator, "regtest")
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
    when(stateView.withdrawalReqProvider.getListOfWithdrawalReqRecords(epochNum, stateView))
      .thenReturn(Seq())

    var res = stateView.getWithdrawalRequests(epochNum)
    assertTrue("The list of withdrawal requests is not empty", res.isEmpty)

    // With 3999 withdrawal requests
    val maxNumOfWithdrawalReqs = MaxWithdrawalReqsNumPerEpoch

    val destAddress = new MCPublicKeyHashProposition(randomBytes(20))
    val listOfWR = (1 to maxNumOfWithdrawalReqs).map(index => {
      WithdrawalRequest(destAddress, ZenWeiConverter.convertZenniesToWei(index))
    })
    when(stateView.withdrawalReqProvider.getListOfWithdrawalReqRecords(epochNum, stateView))
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
      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

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

  @Test
  def testGetNativeSmartContractAddressList(): Unit = {
    var messageProcessors = Seq.empty[MessageProcessor]
    val metadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb = mock[StateDB]
    var stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)

    assertTrue("List of addresses is not empty", stateView.getNativeSmartContractAddressList().isEmpty)

    messageProcessors = Seq(mock[MessageProcessor])
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)
    assertTrue("List of addresses is not empty", stateView.getNativeSmartContractAddressList().isEmpty)

    val mockNativeSmartContract = mock[NativeSmartContractMsgProcessor]
    val mockAddress = new Address("0x0000000000000000000011234561111111111111")
    when(mockNativeSmartContract.contractAddress).thenReturn(mockAddress)
    messageProcessors = Seq(mockNativeSmartContract, mock[MessageProcessor])
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)
    var listOfAddresses = stateView.getNativeSmartContractAddressList()
    assertEquals("Wrong list of addresses size", 1, listOfAddresses.length)
    assertEquals("Wrong address", mockAddress, listOfAddresses.head)

    messageProcessors = Seq(EoaMessageProcessor, mockNativeSmartContract,
      WithdrawalMsgProcessor, mock[MessageProcessor], new CertificateKeyRotationMsgProcessor(mockNetworkParams), mock[EvmMessageProcessor])
    stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)
    listOfAddresses = stateView.getNativeSmartContractAddressList()
    assertEquals("Wrong list of addresses size", 3, listOfAddresses.length)
    assertTrue("Missing mockNativeSmartContract address", listOfAddresses.contains(mockAddress))
    assertTrue("Missing WithdrawalMsgProcessor address", listOfAddresses.contains(WithdrawalMsgProcessor.contractAddress))
    assertTrue("Missing CertificateKeyRotationMsgProcessor address", listOfAddresses.contains(WellKnownAddresses.CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS))


  }

  @Test
  def testGetMcForgerPoolReward(): Unit = {
    val messageProcessors = Seq.empty[MessageProcessor]
    val metadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb = mock[StateDB]
    val stateView = new AccountStateView(metadataStorageView, stateDb, messageProcessors)
    val addr1 = getPrivateKeySecp256k1(1000).publicImage()
    val addr2 = getPrivateKeySecp256k1(1001).publicImage()
    val addr3 = getPrivateKeySecp256k1(1002).publicImage()
    val blockCounters = Map(
      addr1 -> 2L,
      addr2 -> 3L,
      addr3 -> 5L,
    )

    when(stateDb.getBalance(FORGER_POOL_RECIPIENT_ADDRESS)).thenReturn(BigInteger.valueOf(1000L))
    when(metadataStorageView.getForgerBlockCounters).thenAnswer(_ => blockCounters)
    val rewardsBeforeFork = stateView.getMcForgerPoolRewards(intToConsensusEpochNumber(0),MAX_MONEY_IN_WEI)
    assertTrue(rewardsBeforeFork.isEmpty)

    when(metadataStorageView.getConsensusEpochNumber).thenAnswer(_ => Some(36))
    val rewardsAfterFork = stateView.getMcForgerPoolRewards(intToConsensusEpochNumber(36), MAX_MONEY_IN_WEI)
    assertEquals(3, rewardsAfterFork.size)
    assertEquals(BigInteger.valueOf(200L), rewardsAfterFork(addr1))
    assertEquals(BigInteger.valueOf(300L), rewardsAfterFork(addr2))
    assertEquals(BigInteger.valueOf(500L), rewardsAfterFork(addr3))

  }

}
