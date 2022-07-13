package com.horizen.account.state

import com.horizen.account.utils.ZenWeiConverter
import com.horizen.utils.WithdrawalEpochInfo
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._


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

    assertEquals("Wrong initial balance", java.math.BigInteger.ZERO, stateView.getBalance(WithdrawalMsgProcessor.fakeSmartContractAddress.address()).get)
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

    var listOfWR = decodeListOfWithdrawalRequest(wrListInBytes)

    assertTrue("The list of withdrawal requests is not empty", listOfWR.isEmpty)

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

    res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    var wrInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()

    var wt = decodeWithdrawalRequest(wrInBytes)
    assertArrayEquals("Wrong destination address", mcAddr.bytes(), wt.addr.getValue)
    assertEquals("Wrong amount", withdrawalAmount1, wt.amount.getValue)

    val newBalance = stateView.getBalance(msg.getFrom.address()).get
    assertEquals("Wrong value in account balance", 1177, ZenWeiConverter.convertWeiToZennies(newBalance))

    // GetListOfWithdrawalRequest after first withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)

    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    listOfWR = decodeListOfWithdrawalRequest(wrListInBytes)

    assertEquals("Wrong list of withdrawal requests size", 1, listOfWR.size())
    val wr = listOfWR.get(0)
    assertArrayEquals("wrong address", mcAddr.bytes(), wr.addr.getValue)
    assertEquals("wrong amount", withdrawalAmount1, wr.amount.getValue)

    //Creating a second withdrawal request
    val withdrawalAmount2 = ZenWeiConverter.convertZenniesToWei(223)
    msg = getAddWithdrawalRequestMessage(withdrawalAmount2)

    res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    wrInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    wt = decodeWithdrawalRequest(wrInBytes)
    assertArrayEquals("Wrong destination address", mcAddr.bytes(), wt.addr.getValue)
    assertEquals("Wrong amount", withdrawalAmount2, wt.amount.getValue)


    val newBalanceAfterSecondWR = stateView.getBalance(msg.getFrom.address()).get
    val expectedBalance = newBalance.subtract(withdrawalAmount2)
    assertEquals("Wrong value in account balance", expectedBalance, newBalanceAfterSecondWR)

    // GetListOfWithdrawalRequest after second withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)

    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    listOfWR = decodeListOfWithdrawalRequest(wrListInBytes)


    assertEquals("ong list of withdrawal requests size", 2, listOfWR.size())
    val wr1 = listOfWR.get(0)
    assertArrayEquals("wrong address", mcAddr.bytes(), wr1.addr.getValue)
    assertEquals("wrong amount", withdrawalAmount1, wr1.amount.getValue)


    val wr2 = listOfWR.get(1)
    assertArrayEquals("wrong address", mcAddr.bytes(), wr2.addr.getValue)
    assertEquals("wrong amount", withdrawalAmount2, wr2.amount.getValue)

    stateView.stateDb.close()
  }

}
