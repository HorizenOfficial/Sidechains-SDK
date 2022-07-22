package com.horizen.account.state

import com.horizen.account.utils.ZenWeiConverter
import com.horizen.utils.WithdrawalEpochInfo
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.util


class WithdrawalMsgProcessorIntegrationTest
  extends JUnitSuite
    with MockitoSugar
    with WithdrawalMsgProcessorFixture {


  @Before
  def setUp(): Unit = {
  }

  @Test
  def testInit(): Unit = {

    val stateView: AccountStateView = getView
    WithdrawalMsgProcessor.init(stateView)

    assertTrue("Account doesn't exist after init", stateView.accountExists(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))

    assertEquals("Wrong initial balance", java.math.BigInteger.ZERO, stateView.getBalance(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))
    assertEquals("Wrong initial nonce", java.math.BigInteger.ZERO, stateView.stateDb.getNonce(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))
    assertNotNull("Wrong initial code hash", stateView.stateDb.getCodeHash(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))

    stateView.stateDb.close()

  }


  @Test
  def testWithdrawalRequestProcessorIntegration(): Unit = {
    // Setup state view
    val stateView = getView
    WithdrawalMsgProcessor.init(stateView)

    val epochNum = 102
    Mockito.when(metadataStorageView.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(epochNum, 1))

    // GetListOfWithdrawalRequest without withdrawal requests yet

    val msgForListOfWR = getGetListOfWithdrawalRequestMessage(epochNum)

    var res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    var wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()

   // var listOfWR = decodeListOfWithdrawalRequest(wrListInBytes)
    val expectedListOfWR = new util.ArrayList[WithdrawalRequest]()

    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR),wrListInBytes)

    //Invalid request for insufficient balance

    stateView.stateDb.setBalance(msgForListOfWR.getFrom.address(), java.math.BigInteger.ZERO)

    val withdrawalAmount = ZenWeiConverter.convertZenniesToWei(10)
    val msgBalance = getAddWithdrawalRequestMessage(withdrawalAmount)


    res = WithdrawalMsgProcessor.process(msgBalance, stateView)
    assertEquals("Withdrawal request with insufficient balance should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)

    //Creating the first Withdrawal request


    val withdrawalAmount1 = ZenWeiConverter.convertZenniesToWei(123)
    var msg = getAddWithdrawalRequestMessage(withdrawalAmount1)

    val initialBalance = ZenWeiConverter.convertZenniesToWei(1300)
    stateView.addBalance(msg.getFrom.address(), initialBalance)
    var newExpectedWR = WithdrawalRequest(mcAddr,msg.getValue)
    expectedListOfWR.add(newExpectedWR)

    res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    var wrInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()

    assertArrayEquals(newExpectedWR.encode(), wrInBytes)

    val newBalance = stateView.getBalance(msg.getFrom.address())
    assertEquals("Wrong value in account balance", 1177, ZenWeiConverter.convertWeiToZennies(newBalance))

    // GetListOfWithdrawalRequest after first withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)


    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR),wrListInBytes)

    //Creating a second withdrawal request
    val withdrawalAmount2 = ZenWeiConverter.convertZenniesToWei(223)
    msg = getAddWithdrawalRequestMessage(withdrawalAmount2)
    newExpectedWR = WithdrawalRequest(mcAddr,msg.getValue)
    expectedListOfWR.add(newExpectedWR)

    res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    wrInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    assertArrayEquals(newExpectedWR.encode(), wrInBytes)

    val newBalanceAfterSecondWR = stateView.getBalance(msg.getFrom.address())
    val expectedBalance = newBalance.subtract(withdrawalAmount2)
    assertEquals("Wrong value in account balance", expectedBalance, newBalanceAfterSecondWR)

    // GetListOfWithdrawalRequest after second withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)

    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR),wrListInBytes)


    stateView.stateDb.close()
  }

}
