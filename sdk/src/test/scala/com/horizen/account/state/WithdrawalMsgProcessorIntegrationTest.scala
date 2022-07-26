package com.horizen.account.state

import com.horizen.account.events.AddWithdrawalRequest
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import scorex.crypto.hash.Keccak256

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

    val expectedListOfWR = new util.ArrayList[WithdrawalRequest]()

    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)

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
    var newExpectedWR = WithdrawalRequest(mcAddr, msg.getValue)
    expectedListOfWR.add(newExpectedWR)

    val txHash1 = Keccak256.hash("first tx")
    stateView.stateDb.setTxContext(txHash1, 10)

    res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    var wrInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()

    assertArrayEquals(newExpectedWR.encode(), wrInBytes)

    val newBalance = stateView.getBalance(msg.getFrom.address())
    assertEquals("Wrong value in account balance", 1177, ZenWeiConverter.convertWeiToZennies(newBalance))

    //Checking log
    var listOfLogs = stateView.getLogs(txHash1.asInstanceOf[Array[Byte]])
    assertEquals("Wrong number of logs", 1, listOfLogs.length)
    var expectedEvent = AddWithdrawalRequest(msg.getFrom, mcAddr, withdrawalAmount1, epochNum)
    checkEvent(expectedEvent, listOfLogs(0))

    val txHash2 = Keccak256.hash("second tx")
    stateView.stateDb.setTxContext(txHash2, 10)

    // GetListOfWithdrawalRequest after first withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)


    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)

    //Checking that log didn't change
    listOfLogs = stateView.getLogs(txHash2.asInstanceOf[Array[Byte]])
    assertEquals("Wrong number of logs", 0, listOfLogs.length)


    //Creating a second withdrawal request
    val withdrawalAmount2 = ZenWeiConverter.convertZenniesToWei(223)
    msg = getAddWithdrawalRequestMessage(withdrawalAmount2)
    newExpectedWR = WithdrawalRequest(mcAddr, msg.getValue)
    expectedListOfWR.add(newExpectedWR)

    val txHash3 = Keccak256.hash("third tx")
    stateView.stateDb.setTxContext(txHash3, 10)

    res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    wrInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    assertArrayEquals(newExpectedWR.encode(), wrInBytes)

    val newBalanceAfterSecondWR = stateView.getBalance(msg.getFrom.address())
    val expectedBalance = newBalance.subtract(withdrawalAmount2)
    assertEquals("Wrong value in account balance", expectedBalance, newBalanceAfterSecondWR)

    //Checking log
    listOfLogs = stateView.getLogs(txHash3.asInstanceOf[Array[Byte]])
    assertEquals("Wrong number of logs", 1, listOfLogs.length)
    expectedEvent = AddWithdrawalRequest(msg.getFrom, mcAddr, withdrawalAmount2, epochNum)
    checkEvent(expectedEvent, listOfLogs(0))

    // GetListOfWithdrawalRequest after second withdrawal request creation
    res = WithdrawalMsgProcessor.process(msgForListOfWR, stateView)
    assertEquals("Wrong GetListOfWithdrawalRequest result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing return data for GetListOfWithdrawalRequest", res.asInstanceOf[ExecutionSucceeded].hasReturnData)

    wrListInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)


    stateView.stateDb.close()
  }


  def checkEvent(expectedEvent: AddWithdrawalRequest, actualEvent: EvmLog) = {
    assertArrayEquals("Wrong address", WithdrawalMsgProcessor.fakeSmartContractAddress.address(), actualEvent.address.toBytes)
    assertEquals("Wrong number of topics", NumOfIndexedEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", AddNewWithdrawalRequestEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong from address in topic", expectedEvent.from, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.from.getTypeAsString)))
    assertEquals("Wrong mcAddr in topic", expectedEvent.mcDest, decodeEventTopic(actualEvent.topics(2), TypeReference.makeTypeReference(expectedEvent.mcDest.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(TypeReference.makeTypeReference(expectedEvent.value.getTypeAsString), TypeReference.makeTypeReference(expectedEvent.epochNumber.getTypeAsString)).asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong amount in data", expectedEvent.value, listOfDecodedData.get(0))
    assertEquals("Wrong epoch number in data", expectedEvent.epochNumber, listOfDecodedData.get(1))

  }
}
