package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger
import java.util
import scala.util.Random

class WithdrawalMsgProcessorTest extends JUnitSuite with MockitoSugar with WithdrawalMsgProcessorFixture {

  @Before
  def setUp(): Unit = {}

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calcolated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals(
      "Wrong MethodId for GetListOfWithdrawalRequest",
      "251b7baa",
      WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig)
    assertEquals("Wrong MethodId for AddNewWithdrawalReq", "9950a60f", WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig)
  }

  @Test
  def testInit(): Unit = {
    val mockStateView = mock[AccountStateView]
    Mockito
      .when(mockStateView.addAccount(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[Array[Byte]]))
      .thenAnswer(args => {
        assertArrayEquals(
          "Different address expected.",
          WithdrawalMsgProcessor.fakeSmartContractAddress.address(),
          args.getArgument(0))
        assertArrayEquals(
          "Different code hash expected.",
          WithdrawalMsgProcessor.fakeSmartContractCodeHash,
          args.getArgument(1))
      })
    WithdrawalMsgProcessor.init(mockStateView)
  }

  @Test
  def testCanProcess(): Unit = {
    val msg = getAddWithdrawalRequestMessage(BigInteger.ONE)
    val mockStateView = mock[AccountStateView]
    assertTrue(
      "Message for WithdrawalMsgProcessor cannot be processed",
      WithdrawalMsgProcessor.canProcess(msg, mockStateView))
    val wrongAddress = new AddressProposition(BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea"))
    val msgNotProcessable = getMessage(wrongAddress, BigInteger.ZERO, Array.emptyByteArray)
    assertFalse(
      "Message not for WithdrawalMsgProcessor can be processed",
      WithdrawalMsgProcessor.canProcess(msgNotProcessable, mockStateView))
  }

  @Test
  def testProcess(): Unit = {
    val value = BigInteger.valueOf(1000000000L) //1 zenny and 1 wei
    val mockStateView = mock[AccountStateView]

    // msgWithWrongFunctionCall processing should result in ExecutionFailed
    val data = BytesUtils.fromHexString("99")
    val msgWithWrongFunctionCall = getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, value, data)
    assertThrows[ExecutionFailedException] {
      withGas(WithdrawalMsgProcessor.process(msgWithWrongFunctionCall, mockStateView, _))
    }
  }

  @Test
  def testAddWithdrawalRequestFailures(): Unit = {
    val mockStateView = mock[AccountStateView]
    Mockito
      .when(mockStateView.accountExists(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))
      .thenReturn(true)

    // Withdrawal request with invalid data should result in ExecutionFailed
    val withdrawalAmount = ZenWeiConverter.convertZenniesToWei(50)
    val msg = getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, withdrawalAmount, Array.emptyByteArray)
    assertThrows[ExecutionFailedException](withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _)))

    // helper: mock balance call and assert that the withdrawal request throws
    val withdraw = (balance: BigInteger, withdrawalAmount: BigInteger) => {
      val msg = getAddWithdrawalRequestMessage(withdrawalAmount)
      Mockito.when(mockStateView.getBalance(msg.getFrom.address())).thenReturn(balance)
      assertThrows[ExecutionFailedException](withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _)))
    }

    // Withdrawal request with invalid zen amount should result in ExecutionFailed
    withdraw(ZenWeiConverter.convertZenniesToWei(30), 50)
    // Withdrawal request with insufficient balance should result in ExecutionFailed
    withdraw(ZenWeiConverter.convertZenniesToWei(30), ZenWeiConverter.convertZenniesToWei(50))
    // Withdrawal request under dust threshold processing should result in ExecutionFailed
    withdraw(ZenWeiConverter.convertZenniesToWei(1300), ZenWeiConverter.convertZenniesToWei(13))
    // Withdrawal request processing when max number of wt was already reached should result in ExecutionFailed
    val epochNum = 102
    Mockito.when(mockStateView.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(epochNum, 1))
    val key = WithdrawalMsgProcessor.getWithdrawalEpochCounterKey(epochNum)
    val numOfWithdrawalReqs = Bytes.concat(
      new Array[Byte](32 - Ints.BYTES),
      Ints.toByteArray(WithdrawalMsgProcessor.MaxWithdrawalReqsNumPerEpoch))
    Mockito
      .when(mockStateView.getAccountStorage(WithdrawalMsgProcessor.fakeSmartContractAddress.address(), key))
      .thenReturn(numOfWithdrawalReqs)
    withdraw(ZenWeiConverter.convertZenniesToWei(1300), ZenWeiConverter.convertZenniesToWei(60))
  }

  @Test
  def testGetListOfWithdrawalReqs(): Unit = {
    val mockStateView = mock[AccountStateView]

    val epochNum = 102

    // Invalid data
    var msg = getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, BigInteger.ZERO, Array.emptyByteArray)
    Mockito.when(mockStateView.accountExists(msg.getTo.address())).thenReturn(true)

    // Withdrawal request list with invalid data should throw ExecutionFailedException
    assertThrows[ExecutionFailedException](withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _)))

    // No withdrawal requests
    msg = getGetListOfWithdrawalRequestMessage(epochNum)
    val counterKey = WithdrawalMsgProcessor.getWithdrawalEpochCounterKey(epochNum)
    val numOfWithdrawalReqs = Bytes.concat(new Array[Byte](32 - Ints.BYTES), Ints.toByteArray(0))

    Mockito
      .when(mockStateView.getAccountStorage(WithdrawalMsgProcessor.fakeSmartContractAddress.address(), counterKey))
      .thenReturn(numOfWithdrawalReqs)

    var returnData = withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _))
    val expectedListOfWR = new util.ArrayList[WithdrawalRequest]()
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), returnData)

    // With 3999 withdrawal requests
    val maxNumOfWithdrawalReqs = WithdrawalMsgProcessor.MaxWithdrawalReqsNumPerEpoch
    val numOfWithdrawalReqsInBytes =
      Bytes.concat(new Array[Byte](32 - Ints.BYTES), Ints.toByteArray(maxNumOfWithdrawalReqs))
    Mockito
      .when(mockStateView.getAccountStorage(WithdrawalMsgProcessor.fakeSmartContractAddress.address(), counterKey))
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
      .when(mockStateView.getAccountStorageBytes(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[Array[Byte]]))
      .thenAnswer(answer => {
        val key: Array[Byte] = answer.getArgument(1)
        mockWithdrawalRequestsList.get(new ByteArrayWrapper(key))
      })

    returnData = withGas(WithdrawalMsgProcessor.process(msg, mockStateView, _))
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), returnData)
  }
}
