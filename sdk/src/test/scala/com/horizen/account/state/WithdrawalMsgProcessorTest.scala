package com.horizen.account.state

import com.google.common.primitives.Ints
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.{BytesUtils, ListSerializer, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.util
import scala.util.{Random, Success, Try}


class WithdrawalMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture {

  val fakeAddress = new AddressProposition(BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea"))
  val from: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))

  @Before
  def setUp(): Unit = {
  }


  @Test
  def testInit(): Unit = {

    val mockStateView: AccountStateView = mock[AccountStateView]
    Mockito.when(mockStateView.addAccount(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[Array[Byte]])).
      thenAnswer(answer => {
        Try {
          require(util.Arrays.equals(answer.getArgument(0), WithdrawalMsgProcessor.fakeSmartContractAddress.address()))
          val account: Account = answer.getArgument(1).asInstanceOf[Account]
          require(account.nonce == 0)
          require(account.balance == 0)
          require(account.codeHash != null)
          require(account.storageRoot != null)
          mockStateView
        }
      })

    WithdrawalMsgProcessor.init(mockStateView)

  }

  @Test
  def testCanProcess(): Unit = {

    val value: java.math.BigInteger = java.math.BigInteger.ONE
    val msg = getAddWithdrawalRequestMessage(value)
    val mockStateView: AccountStateView = mock[AccountStateView]

    assertTrue("Message for WithdrawalMsgProcessor cannot be processed", WithdrawalMsgProcessor.canProcess(msg, mockStateView))

    val msgNotProcessable = getMessage(fakeAddress, java.math.BigInteger.ZERO, Array.emptyByteArray)
    assertFalse("Message not for WithdrawalMsgProcessor can be processed", WithdrawalMsgProcessor.canProcess(msgNotProcessable, mockStateView))

  }


  @Test
  def testProcess(): Unit = {
    val value: java.math.BigInteger = java.math.BigInteger.valueOf(1000000000L) //1 zenny and 1 wei

    val msgForWrongProcessor = getMessage(fakeAddress, value,Array.emptyByteArray)
    val mockStateView: AccountStateView = mock[AccountStateView]
    assertEquals("msgForWrongProcessor processing should result in InvalidMessage", classOf[InvalidMessage], WithdrawalMsgProcessor.process(msgForWrongProcessor, mockStateView).getClass)

    val data = BytesUtils.fromHexString("99")
    val msgWithWrongFunctionCall = getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, value,data)
    assertEquals("msgWithWrongFunctionCall processing should result in ExecutionFailed", classOf[ExecutionFailed], WithdrawalMsgProcessor.process(msgWithWrongFunctionCall, mockStateView).getClass)

    val mockMsg = mock[Message]
    Mockito.when(mockMsg.getTo).thenReturn(WithdrawalMsgProcessor.fakeSmartContractAddress)
    val expException = new RuntimeException()
    Mockito.when(mockMsg.getData).thenThrow(expException)
    val res = WithdrawalMsgProcessor.process(mockMsg, mockStateView)
    assertEquals("msgWithWrongFunctionCall processing should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(expException, res.asInstanceOf[ExecutionFailed].getReason)

  }


  @Test
  def testAddWithdrawalRequestFailures(): Unit = {
    val mockStateView = mock[AccountStateView]

    //Invalid data
    var withdrawalAmount: java.math.BigInteger = ZenWeiConverter.convertZenniesToWei(50)
    var msg = getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, withdrawalAmount, Array.emptyByteArray)
    var res = WithdrawalMsgProcessor.process(msg, mockStateView)
    assertEquals("Withdrawal request with invalid data should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)


    //Invalid zen amount
    withdrawalAmount = java.math.BigInteger.valueOf(50)
    val msgInvalidAmount = getAddWithdrawalRequestMessage(withdrawalAmount)
    Mockito.when(mockStateView.getBalance(from.address())).thenReturn(ZenWeiConverter.convertZenniesToWei(30))
    res = WithdrawalMsgProcessor.process(msgInvalidAmount, mockStateView)
    assertEquals("Withdrawal request with invalid zen amount should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)

    //Insufficient balance
    withdrawalAmount = ZenWeiConverter.convertZenniesToWei(50)
    val msgBalance = getAddWithdrawalRequestMessage(withdrawalAmount)

    Mockito.when(mockStateView.getBalance(from.address())).thenReturn(ZenWeiConverter.convertZenniesToWei(30))
    res = WithdrawalMsgProcessor.process(msgBalance, mockStateView)
    assertEquals("Withdrawal request with insufficient balance should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)


    //Under dust threshold
    val withdrawalAmountUnderDustThreshold: java.math.BigInteger = ZenWeiConverter.convertZenniesToWei(13)
    val msgUnderDustThres = getAddWithdrawalRequestMessage(withdrawalAmountUnderDustThreshold)


    Mockito.when(mockStateView.getBalance(from.address())).thenReturn(ZenWeiConverter.convertZenniesToWei(1300))
    res = WithdrawalMsgProcessor.process(msgUnderDustThres, mockStateView)
    assertEquals("Withdrawal request under dust threshold processing should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)

    //Max number of Withdrawal Requests reached
    withdrawalAmount = ZenWeiConverter.convertZenniesToWei(50)
    msg = getAddWithdrawalRequestMessage(withdrawalAmount)
    Mockito.when(mockStateView.getBalance(from.address())).thenReturn(ZenWeiConverter.convertZenniesToWei(1300))
    val epochNum = 102
    Mockito.when(mockStateView.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(epochNum, 1))
    val key = WithdrawalMsgProcessor.getWithdrawalEpochCounterKey(epochNum)
    val numOfWithdrawalReqs = Ints.toByteArray(WithdrawalMsgProcessor.MAX_WITHDRAWAL_REQS_NUM_PER_EPOCH)

    Mockito.when(mockStateView.getAccountStorageBytes(WithdrawalMsgProcessor.fakeSmartContractAddress.address(), key)).thenReturn(Success(numOfWithdrawalReqs))
    res = WithdrawalMsgProcessor.process(msg, mockStateView)
    assertEquals("Withdrawal request processing when max number of wt was already reached  should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)


  }

  @Test
  def testGetListOfWithdrawalReqs(): Unit = {
    val mockStateView = mock[AccountStateView]
    val epochNum = 102

    //Invalid data
    var msg = getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, java.math.BigInteger.ZERO, Array.emptyByteArray)
    var res = WithdrawalMsgProcessor.process(msg, mockStateView)
    assertEquals("Withdrawal request list with invalid data should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)

    // No withdrawal requests

    msg = getGetListOfWithdrawalRequestMessage(epochNum)
    val key = WithdrawalMsgProcessor.getWithdrawalEpochCounterKey(epochNum)
    val numOfWithdrawalReqs = Ints.toByteArray(0)

    Mockito.when(mockStateView.getAccountStorageBytes(WithdrawalMsgProcessor.fakeSmartContractAddress.address(), key)).thenReturn(Success(numOfWithdrawalReqs))

    res = WithdrawalMsgProcessor.process(msg, mockStateView)
    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    var wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()

    var withdrawalRequestSerializer = new ListSerializer[WithdrawalRequest](WithdrawalRequestSerializer)
    var listOfWR = withdrawalRequestSerializer.parseBytes(wrListInBytes)

    assertTrue("The list of withdrawal requests is not empty", listOfWR.isEmpty)


    // With 3900 withdrawal requests
    val maxNumOfWithdrawalReqs = WithdrawalMsgProcessor.MAX_WITHDRAWAL_REQS_NUM_PER_EPOCH

    Mockito.when(mockStateView.getAccountStorageBytes(WithdrawalMsgProcessor.fakeSmartContractAddress.address(), key)).thenReturn(Success(Ints.toByteArray(maxNumOfWithdrawalReqs)))
    val destAddress = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    (1 to maxNumOfWithdrawalReqs).foreach(index => {
      val wr = WithdrawalRequest(destAddress, ZenWeiConverter.convertZenniesToWei(index))
      val key = WithdrawalMsgProcessor.getWithdrawalRequestsKey(epochNum, index)
      Mockito.when(mockStateView.getAccountStorageBytes(WithdrawalMsgProcessor.fakeSmartContractAddress.address(), key)).thenReturn(Success(WithdrawalRequestSerializer.toBytes(wr)))

    }
    )

    res = WithdrawalMsgProcessor.process(msg, mockStateView)
    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()

    withdrawalRequestSerializer = new ListSerializer[WithdrawalRequest](WithdrawalRequestSerializer)
    listOfWR = withdrawalRequestSerializer.parseBytes(wrListInBytes)

    assertEquals("Wrong list of withdrawal requests size", maxNumOfWithdrawalReqs, listOfWR.size())
    (0 until maxNumOfWithdrawalReqs).foreach(index => {
      val wr = listOfWR.get(index)
      assertEquals("wrong address", destAddress, wr.proposition)
      assertEquals("wrong amount", ZenWeiConverter.convertZenniesToWei(index + 1), wr.value)
    })

  }


}
