package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.utils.{FeeUtils, ZenWeiConverter}
import com.horizen.evm.utils.Address
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.WithdrawalEpochUtils.MaxWithdrawalReqsNumPerEpoch
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert._
import org.junit._
import org.mockito.ArgumentMatchers.any
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger
import java.util
import scala.util.Random

class WithdrawalMsgProcessorTest extends JUnitSuite with MockitoSugar with WithdrawalMsgProcessorFixture {

  var mockStateView: AccountStateView = _

  @Before
  def setUp(): Unit = {
    mockStateView = mock[AccountStateView]
    Mockito
      .when(mockStateView.getGasTrackedView(any()))
      .thenReturn(mockStateView)
  }

  @Test
  def testMethodIds(): Unit = {
    // The expected methodIds were calcolated using this site: https://emn178.github.io/online-tools/keccak_256.html
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

    // msgWithWrongFunctionCall processing should result in ExecutionFailed
    val data = BytesUtils.fromHexString("99")
    val msgWithWrongFunctionCall = getMessage(WithdrawalMsgProcessor.contractAddress, value, data)
    assertThrows[ExecutionFailedException] {
      withGas(WithdrawalMsgProcessor.process(msgWithWrongFunctionCall, mockStateView, _, defaultBlockContext))
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
    assertThrows[ExecutionFailedException](
      withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _, defaultBlockContext))
    )

    // helper: mock balance call and assert that the withdrawal request throws
    val withdraw = (balance: BigInteger, withdrawalAmount: BigInteger, blockContext: BlockContext) => {
      val msg = addWithdrawalRequestMessage(withdrawalAmount)
      Mockito.when(mockStateView.getBalance(msg.getFrom)).thenReturn(balance)
      assertThrows[ExecutionFailedException](
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
    val testEpochBlockContext = new BlockContext(Address.ZERO, 0, 0, FeeUtils.GAS_LIMIT, 0, 0, epochNum, 1)
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

    // Withdrawal request list with invalid data should throw ExecutionFailedException
    assertThrows[ExecutionFailedException](
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
