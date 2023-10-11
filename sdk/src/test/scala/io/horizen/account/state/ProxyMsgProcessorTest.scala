package io.horizen.account.state

import io.horizen.account.fork.ContractInteroperabilityFork
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.consensus.intToConsensusEpochNumber
import io.horizen.evm.Address
import io.horizen.fixtures.StoreFixture
import io.horizen.fork.{ForkConfigurator, ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch}
import io.horizen.params.NetworkParams
import io.horizen.utils.{BytesUtils, Pair}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.util
import scala.jdk.CollectionConverters.seqAsJavaListConverter

class ProxyMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture
    with StoreFixture {

  val MOCK_FORK_POINT: Int = 100

  class TestOptionalForkConfigurator extends ForkConfigurator {
    override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 0)

    override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
      Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
        new Pair(SidechainForkConsensusEpoch(MOCK_FORK_POINT, MOCK_FORK_POINT, MOCK_FORK_POINT), ContractInteroperabilityFork(true)),
      ).asJava
  }

  override val defaultBlockContext = new BlockContext(
    Address.ZERO,
    0,
    0,
    DefaultGasFeeFork.blockGasLimit,
    0,
    MOCK_FORK_POINT,
    0,
    1,
    MockedHistoryBlockHashProvider,
    new io.horizen.evm.Hash(new Array[Byte](32))
  )

  @Before
  def init(): Unit = {
    ForkManagerUtil.initializeForkManager(new TestOptionalForkConfigurator, "regtest")
    // by default start with fork active
    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(Option(intToConsensusEpochNumber(MOCK_FORK_POINT)))
  }

  val validWeiAmount: BigInteger = new BigInteger("10000000000")

  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val messageProcessor: ProxyMsgProcessor = ProxyMsgProcessor(mockNetworkParams)
  val contractAddress: Address = messageProcessor.contractAddress

  val scAddrStr1: String = "00C8F107a09cd4f463AFc2f1E6E5bF6022Ad4600"
  val scAddressObj1 = new Address("0x"+scAddrStr1)

  def randomNonce: BigInteger = randomU256

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calculated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals("Wrong MethodId for InvokeSmartContractCallCmd", "9b679b4d", ProxyMsgProcessor.InvokeSmartContractCallCmd)
    assertEquals("Wrong MethodId for InvokeSmartContractStaticCallCmd", "1c6af61c", ProxyMsgProcessor.InvokeSmartContractStaticCallCmd)
  }

  @Test
  def testInit(): Unit = {

    usingView(messageProcessor) { view =>

      assertTrue(messageProcessor.isForkActive(view.getConsensusEpochNumberAsInt))
      assertFalse(messageProcessor.initDone(view))

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      assertTrue(view.accountExists(contractAddress))
      assertFalse(view.isEoaAccount(contractAddress))
      assertTrue(view.isSmartContractAccount(contractAddress))
      assertTrue(messageProcessor.initDone(view))

      assertArrayEquals(messageProcessor.contractCodeHash, view.getCodeHash(contractAddress))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testInitBeforeFork(): Unit = {

    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(
      Option(intToConsensusEpochNumber(MOCK_FORK_POINT - 1)))

    usingView(messageProcessor) { view =>

      assertFalse(view.accountExists(contractAddress))
      assertFalse(messageProcessor.initDone(view))

      assertFalse(messageProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // assert no initialization took place
      assertFalse(messageProcessor.initDone(view))
    }
  }

  @Test
  def testInitWithAccountAlreadyExisting(): Unit = {
     usingView(messageProcessor) { view =>
       view.addAccount(contractAddress, Keccak256.hash("whatever"))
       view.commit(bytesToVersion(getVersion.data()))

       messageProcessor.init(view, view.getConsensusEpochNumberAsInt)
       assertTrue(view.accountExists(contractAddress))
       assertFalse(view.isEoaAccount(contractAddress))
       assertTrue(view.isSmartContractAccount(contractAddress))
       assertTrue(messageProcessor.initDone(view))
       view.commit(bytesToVersion(getVersion.data()))
     }
  }

  @Test
  def testDoubleInit(): Unit = {

    usingView(messageProcessor) { view =>

      assertTrue(messageProcessor.isForkActive(view.getConsensusEpochNumberAsInt))
      assertFalse(messageProcessor.initDone(view))

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      assertTrue(messageProcessor.initDone(view))

      view.commit(bytesToVersion(getVersion.data()))

      val ex = intercept[MessageProcessorInitializationException] {
        messageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      }
      assertTrue(ex.getMessage.contains("already init"))
    }
  }


  @Test
  def testCanProcess(): Unit = {
    usingView(messageProcessor) { view =>

      // assert no initialization took place yet
      assertFalse(messageProcessor.initDone(view))

      assertTrue(messageProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      // correct contract address
      assertTrue(TestContext.canProcess(messageProcessor, getMessage(messageProcessor.contractAddress), view, view.getConsensusEpochNumberAsInt))

      // check initialization took place
      assertTrue(view.accountExists(contractAddress))
      assertTrue(view.isSmartContractAccount(contractAddress))
      assertFalse(view.isEoaAccount(contractAddress))

      // call a second time for checking it does not do init twice (would assert)
      assertTrue(TestContext.canProcess(messageProcessor, getMessage(messageProcessor.contractAddress), view, view.getConsensusEpochNumberAsInt))

      // wrong address
      assertFalse(TestContext.canProcess(messageProcessor, getMessage(randomAddress), view, view.getConsensusEpochNumberAsInt))
      // contract deployment: to == null
      assertFalse(TestContext.canProcess(messageProcessor, getMessage(null), view, view.getConsensusEpochNumberAsInt))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testCanNotProcessBeforeFork(): Unit = {

    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(
      Option(intToConsensusEpochNumber(1)))

    usingView(messageProcessor) { view =>

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)
      val txHash1 = Keccak256.hash("tx")
      view.setupTxContext(txHash1, 10)
      createSenderAccount(view, initialAmount, scAddressObj1)
      val cmdInput = InvokeSmartContractCmdInput(WithdrawalMsgProcessor.contractAddress, WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig)
      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(ProxyMsgProcessor.InvokeSmartContractStaticCallCmd) ++ data,
        randomNonce,
        scAddressObj1
      )

      assertFalse(messageProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      // correct contract address and message but fork not yet reached
      assertFalse(TestContext.canProcess(messageProcessor, msg, view, view.getConsensusEpochNumberAsInt))

      // the init did not take place
      assertFalse(messageProcessor.initDone(view))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessBeforeFork(): Unit = {

    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(
      Option(intToConsensusEpochNumber(1)))

    usingView(messageProcessor) { view =>

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)
      val txHash1 = Keccak256.hash("tx")
      view.setupTxContext(txHash1, 10)
      createSenderAccount(view, initialAmount, scAddressObj1)
      val cmdInput = InvokeSmartContractCmdInput(WithdrawalMsgProcessor.contractAddress, WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig)
      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(ProxyMsgProcessor.InvokeSmartContractStaticCallCmd) ++ data,
        randomNonce,
        scAddressObj1
      )

      assertFalse(messageProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      val blockContext = new BlockContext(
        Address.ZERO,
        0,
        0,
        DefaultGasFeeFork.blockGasLimit,
        0,
        view.getConsensusEpochNumberAsInt,
        0,
        1,
        MockedHistoryBlockHashProvider,
        new io.horizen.evm.Hash(new Array[Byte](32))
      )
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msg, view, blockContext, _))
      }
      assertTrue(ex.getMessage.contains("fork not active"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

}
