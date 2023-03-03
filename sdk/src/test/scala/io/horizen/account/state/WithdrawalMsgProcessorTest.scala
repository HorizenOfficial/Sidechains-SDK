package io.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.utils.{FeeUtils, ZenWeiConverter}
import io.horizen.proposition.MCPublicKeyHashProposition
import io.horizen.utils.WithdrawalEpochUtils.MaxWithdrawalReqsNumPerEpoch
import io.horizen.utils.{ByteArrayWrapper, BytesUtils}
import io.horizen.evm.{Address, Hash}
import io.horizen.fixtures.StoreFixture
import org.junit.Assert._
import org.junit._
import org.mockito.ArgumentMatchers.any
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.bytesToVersion

import java.math.BigInteger
import java.util
import java.util.Optional
import scala.util.Random

class WithdrawalMsgProcessorTest extends JUnitSuite with MockitoSugar with WithdrawalMsgProcessorFixture with StoreFixture{

  var mockStateView: AccountStateView = _

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = 0): Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      origin,
      Optional.of(WithdrawalMsgProcessor.contractAddress), // to
      0, // gasPrice
      0, // gasFeeCap
      0, // gasTipCap
      0, // gasLimit
      value,
      nonce,
      data,
      false)
  }

  def randomNonce: BigInteger = randomU256

  @Before
  def setUp(): Unit = {
    mockStateView = mock[AccountStateView]
    Mockito
      .when(mockStateView.getGasTrackedView(any()))
      .thenReturn(mockStateView)
  }

  @Test
  def testMethodIds(): Unit = {
    // The expected methodIds were calculated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals(
      "Wrong MethodId for GetListOfWithdrawalReqs",
      "ed63ec62",
      WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig
    )
    assertEquals(
      "Wrong MethodId for AddNewWithdrawalReq",
      "4267ec5e",
      WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig
    )
  }

  @Test
  def testInit(): Unit = {
    Mockito
      .when(mockStateView.addAccount(ArgumentMatchers.any[Address], ArgumentMatchers.any[Array[Byte]]))
      .thenAnswer(args => {
        assertEquals("Different address expected.", WithdrawalMsgProcessor.contractAddress, args.getArgument(0))
        assertArrayEquals("Different code expected.", WithdrawalMsgProcessor.contractCode, args.getArgument(1))
      })
    WithdrawalMsgProcessor.init(mockStateView)
  }

  @Test
  def testCanProcess(): Unit = {
    val msg = addWithdrawalRequestMessage(BigInteger.ONE)
    assertTrue(
      "Message for WithdrawalMsgProcessor cannot be processed",
      WithdrawalMsgProcessor.canProcess(msg, mockStateView)
    )
    val wrongAddress = new Address("0x35fdd51e73221f467b40946c97791a3e19799bea")
    val msgNotProcessable = getMessage(wrongAddress, BigInteger.ZERO, Array.emptyByteArray)
    assertFalse(
      "Message not for WithdrawalMsgProcessor can be processed",
      WithdrawalMsgProcessor.canProcess(msgNotProcessable, mockStateView)
    )
  }

  @Test
  def testProcess(): Unit = {
    val value = BigInteger.valueOf(1000000000L) // 1 zenny and 1 wei

    // msgWithWrongFunctionCall processing should result in ExecutionRevertedException
    val data = BytesUtils.fromHexString("99")
    val msgWithWrongFunctionCall = getMessage(WithdrawalMsgProcessor.contractAddress, value, data)
    assertThrows[ExecutionRevertedException] {
      withGas(WithdrawalMsgProcessor.process(msgWithWrongFunctionCall, mockStateView, _, defaultBlockContext))
    }
  }


  @Test
  def testProcessShortOpCode(): Unit = {
    usingView(WithdrawalMsgProcessor) { view =>
      WithdrawalMsgProcessor.init(view)
      val args: Array[Byte] = new Array[Byte](0)
      val opCode = BytesUtils.fromHexString("ac")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid (1 byte instead of 4 bytes)
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, WithdrawalMsgProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Data length"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessInvalidOpCode(): Unit = {
    usingView(WithdrawalMsgProcessor) { view =>
      WithdrawalMsgProcessor.init(view)
      val args: Array[Byte] = BytesUtils.fromHexString("1234567890")
      val opCode = BytesUtils.fromHexString("abadc0de")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, WithdrawalMsgProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Requested function does not exist"))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testAddWithdrawalRequestFailures(): Unit = {
    Mockito
      .when(mockStateView.accountExists(WithdrawalMsgProcessor.contractAddress))
      .thenReturn(true)

    // Withdrawal request with invalid data should result in ExecutionFailed
    val withdrawalAmount = ZenWeiConverter.convertZenniesToWei(50)
    val msg = getMessage(WithdrawalMsgProcessor.contractAddress, withdrawalAmount, Array.emptyByteArray)
    assertThrows[ExecutionRevertedException](
      withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    )

    // helper: mock balance call and assert that the withdrawal request throws
    val withdraw = (balance: BigInteger, withdrawalAmount: BigInteger, blockContext: BlockContext) => {
      val msg = addWithdrawalRequestMessage(withdrawalAmount)
      Mockito.when(mockStateView.getBalance(msg.getFrom)).thenReturn(balance)
      assertThrows[ExecutionRevertedException](
        withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, blockContext))
      )
    }

    // Withdrawal request with invalid zen amount should result in ExecutionFailed
    withdraw(ZenWeiConverter.convertZenniesToWei(30), 50, defaultBlockContext)
    // Withdrawal request with insufficient balance should result in ExecutionFailed
    withdraw(ZenWeiConverter.convertZenniesToWei(30), ZenWeiConverter.convertZenniesToWei(50), defaultBlockContext)
    // Withdrawal request under dust threshold processing should result in ExecutionFailed
    withdraw(ZenWeiConverter.convertZenniesToWei(1300), ZenWeiConverter.convertZenniesToWei(13), defaultBlockContext)
    // Withdrawal request processing when max number of wt was already reached should result in ExecutionFailed
    val epochNum = 102
    val testEpochBlockContext =
      new BlockContext(Address.ZERO, 0, 0, FeeUtils.GAS_LIMIT, 0, 0, epochNum, 1, MockedHistoryBlockHashProvider, Hash.ZERO)
    val key = WithdrawalMsgProcessor.getWithdrawalEpochCounterKey(epochNum)
    val numOfWithdrawalReqs = Bytes.concat(
      new Array[Byte](32 - Ints.BYTES),
      Ints.toByteArray(MaxWithdrawalReqsNumPerEpoch)
    )
    Mockito
      .when(mockStateView.getAccountStorage(WithdrawalMsgProcessor.contractAddress, key))
      .thenReturn(numOfWithdrawalReqs)
    withdraw(ZenWeiConverter.convertZenniesToWei(1300), ZenWeiConverter.convertZenniesToWei(60), testEpochBlockContext)
  }

  @Test
  def testGetListOfWithdrawalReqs(): Unit = {
    val epochNum = 102

    // Invalid data
    var msg = getMessage(WithdrawalMsgProcessor.contractAddress, BigInteger.ZERO, Array.emptyByteArray)
    Mockito.when(mockStateView.accountExists(WithdrawalMsgProcessor.contractAddress)).thenReturn(true)

    // Withdrawal request list with invalid data should throw ExecutionRevertedException
    assertThrows[ExecutionRevertedException](
      withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    )

    // No withdrawal requests
    msg = listWithdrawalRequestsMessage(epochNum)
    val counterKey = WithdrawalMsgProcessor.getWithdrawalEpochCounterKey(epochNum)
    val numOfWithdrawalReqs = Bytes.concat(new Array[Byte](32 - Ints.BYTES), Ints.toByteArray(0))

    Mockito
      .when(mockStateView.getAccountStorage(WithdrawalMsgProcessor.contractAddress, counterKey))
      .thenReturn(numOfWithdrawalReqs)

    var returnData = withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    val expectedListOfWR = new util.ArrayList[WithdrawalRequest]()
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), returnData)

    // With 3999 withdrawal requests
    val maxNumOfWithdrawalReqs = MaxWithdrawalReqsNumPerEpoch
    val numOfWithdrawalReqsInBytes =
      Bytes.concat(new Array[Byte](32 - Ints.BYTES), Ints.toByteArray(maxNumOfWithdrawalReqs))
    Mockito
      .when(mockStateView.getAccountStorage(WithdrawalMsgProcessor.contractAddress, counterKey))
      .thenReturn(numOfWithdrawalReqsInBytes)

    val destAddress = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    val mockWithdrawalRequestsList = new util.HashMap[ByteArrayWrapper, Array[Byte]](maxNumOfWithdrawalReqs)

    for (index <- 1 to maxNumOfWithdrawalReqs) {
      val wr = WithdrawalRequest(destAddress, ZenWeiConverter.convertZenniesToWei(index))
      expectedListOfWR.add(wr)
      val key = WithdrawalMsgProcessor.getWithdrawalRequestsKey(epochNum, index)
      mockWithdrawalRequestsList.put(new ByteArrayWrapper(key), wr.bytes)
    }

    Mockito
      .when(mockStateView.getAccountStorageBytes(ArgumentMatchers.any[Address], ArgumentMatchers.any[Array[Byte]]))
      .thenAnswer(answer => {
        val key: Array[Byte] = answer.getArgument(1)
        mockWithdrawalRequestsList.get(new ByteArrayWrapper(key))
      })

    returnData = withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, defaultBlockContext), 10000000)
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), returnData)
  }

  @Test
  def testRejectSendingInvalidValueToGetListOfWithdrawal(): Unit = {
    var msg = getMessage(
      WithdrawalMsgProcessor.contractAddress,
      BigInteger.ONE,
      BytesUtils.fromHexString(WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig)
    )

    assertThrows[ExecutionRevertedException] {
      withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }

    msg = getMessage(
      WithdrawalMsgProcessor.contractAddress,
      BigInteger.valueOf(-1),
      BytesUtils.fromHexString(WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig)
    )

    assertThrows[ExecutionRevertedException] {
      withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    }
  }
}
