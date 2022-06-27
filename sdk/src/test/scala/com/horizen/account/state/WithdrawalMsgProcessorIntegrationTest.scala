package com.horizen.account.state

import com.horizen.account.utils.ZenWeiConverter
import com.horizen.utils.{ListSerializer, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._


class WithdrawalMsgProcessorIntegrationTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture {


  @Before
  def setUp(): Unit = {
  }


  def deserializeListOfWithdrawalRequest(wrListInBytes: Array[Byte]): java.util.List[WithdrawalRequest] = {
    val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequest](WithdrawalRequestSerializer)
    withdrawalRequestSerializer.parseBytes(wrListInBytes)
  }

  @Test
  def testInit(): Unit = {

    val stateView: AccountStateView = getView
    WithdrawalMsgProcessor.init(stateView)

    assertTrue("Account doesn't exist after init", stateView.accountExists(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))

    assertEquals("Wrong initial balance", java.math.BigInteger.ZERO, stateView.getBalance(WithdrawalMsgProcessor.fakeSmartContractAddress.address()).get)
    assertEquals("Wrong initial nonce", java.math.BigInteger.ZERO, stateView.stateDb.getNonce(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))
    assertNotNull("Wrong initial codehash", stateView.stateDb.getCodeHash(WithdrawalMsgProcessor.fakeSmartContractAddress.address()))

    stateView.stateDb.close()

  }


  @Test
  def testWithdrawalRequestProcessorIntegration(): Unit = {
    // Setup state view
    val stateView = getView
    WithdrawalMsgProcessor.init(stateView)

    val epochNum = 102
    Mockito.when(stateView.metadataStorageView.getWithdrawalEpochInfo).thenReturn(Some(WithdrawalEpochInfo(epochNum, 1)))

    // GetListOfWithdrawalRequest without withdrawal requests yet

    val msgForListOfWR = getGetListOfWithdrawalRequestMessage(epochNum)

    var res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    var wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()

    var listOfWR = deserializeListOfWithdrawalRequest(wrListInBytes)

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
    var wt = WithdrawalRequestSerializer.parseBytes(wrInBytes)
    assertEquals("Wrong destination address", mcAddr, wt.proposition)
    assertEquals("Wrong amount", withdrawalAmount1, wt.value)

    val newBalance = stateView.getBalance(msg.getFrom.address()).get
    assertEquals("Wrong value in account balance", 1177, ZenWeiConverter.convertWeiToZennies(newBalance))

    // GetListOfWithdrawalRequest after first withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)

    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    listOfWR = deserializeListOfWithdrawalRequest(wrListInBytes)

    assertEquals("Wrong list of withdrawal requests size", 1, listOfWR.size())
    val wr = listOfWR.get(0)
    assertEquals("wrong address", mcAddr, wr.proposition)
    assertEquals("wrong amount", withdrawalAmount1, wr.value)

    //Creating a second withdrawal request
    val withdrawalAmount2 = ZenWeiConverter.convertZenniesToWei(223)
    msg = getAddWithdrawalRequestMessage(withdrawalAmount2)

    res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    wrInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    wt = WithdrawalRequestSerializer.parseBytes(wrInBytes)
    assertEquals("Wrong destination address", mcAddr, wt.proposition)
    assertEquals("Wrong amount", withdrawalAmount2, wt.value)

    val newBalanceAfterSecondWR = stateView.getBalance(msg.getFrom.address()).get
    val expectedBalance = newBalance.subtract(withdrawalAmount2)
    assertEquals("Wrong value in account balance", expectedBalance, newBalanceAfterSecondWR)

    // GetListOfWithdrawalRequest after second withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)

    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    listOfWR = deserializeListOfWithdrawalRequest(wrListInBytes)

    assertEquals("Wrong list of withdrawal requests size", 2, listOfWR.size())
    val wr1 = listOfWR.get(0)
    assertEquals("wrong address", mcAddr, wr1.proposition)
    assertEquals("wrong amount", withdrawalAmount1, wr1.value)

    val wr2 = listOfWR.get(1)
    assertEquals("wrong address", mcAddr, wr2.proposition)
    assertEquals("wrong amount", withdrawalAmount2, wr2.value)

    stateView.stateDb.close()
  }

}
