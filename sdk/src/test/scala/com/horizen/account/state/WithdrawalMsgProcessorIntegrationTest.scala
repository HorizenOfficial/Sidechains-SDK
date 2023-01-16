package com.horizen.account.state

import com.horizen.account.utils.FeeUtils
import com.horizen.account.events.AddWithdrawalRequest
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.interop.EvmLog
import com.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import scorex.crypto.hash.Keccak256
import java.math.BigInteger
import java.util

class WithdrawalMsgProcessorIntegrationTest
    extends JUnitSuite
    with MockitoSugar
    with WithdrawalMsgProcessorFixture
    with ClosableResourceHandler {

  @Before
  def setUp(): Unit = {}

  @Test
  def testInit(): Unit = {
    val address = WithdrawalMsgProcessor.contractAddress
    usingView(WithdrawalMsgProcessor) { view =>
      WithdrawalMsgProcessor.init(view)
      assertTrue("Account doesn't exist after init", view.accountExists(address))
      assertEquals("Wrong initial balance", BigInteger.ZERO, view.getBalance(address))
      assertEquals("Wrong initial nonce", BigInteger.ZERO, view.getNonce(address))
      assertArrayEquals(
        "Wrong initial code hash",
        WithdrawalMsgProcessor.contractCodeHash,
        view.getCodeHash(address))
    }
  }

  @Test
  def testWithdrawalRequestProcessorIntegration(): Unit = {
    usingView(WithdrawalMsgProcessor) { view =>
      WithdrawalMsgProcessor.init(view)

      val withdrawalEpoch = 102
      val blockContext = new BlockContext(Array.fill(20)(0), 0, 0, FeeUtils.GAS_LIMIT, 0, 0, withdrawalEpoch, 1)

      // GetListOfWithdrawalRequest without withdrawal requests yet
      val msgForListOfWR = listWithdrawalRequestsMessage(withdrawalEpoch)
      var wrListInBytes = withGas(WithdrawalMsgProcessor.process(msgForListOfWR, view, _, blockContext))
      val expectedListOfWR = new util.ArrayList[WithdrawalRequest]()
      assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)

      // Invalid request for insufficient balance
      view.subBalance(msgForListOfWR.getFrom.address(), view.getBalance(msgForListOfWR.getFrom.address()))
      val withdrawalAmount = ZenWeiConverter.convertZenniesToWei(10)
      val msgBalance = addWithdrawalRequestMessage(withdrawalAmount)
      // Withdrawal request with insufficient balance should result in ExecutionFailed
      assertThrows[ExecutionFailedException](withGas(WithdrawalMsgProcessor.process(msgBalance, view, _, blockContext)))

      // Creating the first Withdrawal request
      val withdrawalAmount1 = ZenWeiConverter.convertZenniesToWei(123)
      var msg = addWithdrawalRequestMessage(withdrawalAmount1)
      val initialBalance = ZenWeiConverter.convertZenniesToWei(1300)
      view.addBalance(msg.getFrom.address(), initialBalance)
      var newExpectedWR = WithdrawalRequest(mcAddr, msg.getValue)
      expectedListOfWR.add(newExpectedWR)

      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val wrInBytes = withGas(WithdrawalMsgProcessor.process(msg, view, _, blockContext))
      assertArrayEquals(newExpectedWR.encode(), wrInBytes)
      val newBalance = view.getBalance(msg.getFrom.address())
      assertEquals("Wrong value in account balance", 1177, ZenWeiConverter.convertWeiToZennies(newBalance))

      // Checking log
      var listOfLogs = view.getLogs(txHash1.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      var expectedEvent = AddWithdrawalRequest(msg.getFrom, mcAddr, withdrawalAmount1, withdrawalEpoch)
      checkEvent(expectedEvent, listOfLogs(0))

      val txHash2 = Keccak256.hash("second tx")
      view.setupTxContext(txHash2, 10)

      // GetListOfWithdrawalRequest after first withdrawal request creation
      wrListInBytes = withGas(WithdrawalMsgProcessor.process(msgForListOfWR, view, _, blockContext))
      assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)

      // Checking that log didn't change
      listOfLogs = view.getLogs(txHash2.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 0, listOfLogs.length)

      // Creating a second withdrawal request
      val withdrawalAmount2 = ZenWeiConverter.convertZenniesToWei(223)
      msg = addWithdrawalRequestMessage(withdrawalAmount2)
      newExpectedWR = WithdrawalRequest(mcAddr, msg.getValue)
      expectedListOfWR.add(newExpectedWR)

      val txHash3 = Keccak256.hash("third tx")
      view.setupTxContext(txHash3, 10)

      val wrInBytes2 = withGas(WithdrawalMsgProcessor.process(msg, view, _, blockContext))
      assertArrayEquals(newExpectedWR.encode(), wrInBytes2)

      val newBalanceAfterSecondWR = view.getBalance(msg.getFrom.address())
      val expectedBalance = newBalance.subtract(withdrawalAmount2)
      assertEquals("Wrong value in account balance", expectedBalance, newBalanceAfterSecondWR)

      // Checking log
      listOfLogs = view.getLogs(txHash3.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      expectedEvent = AddWithdrawalRequest(msg.getFrom, mcAddr, withdrawalAmount2, withdrawalEpoch)
      checkEvent(expectedEvent, listOfLogs(0))

      // GetListOfWithdrawalRequest after second withdrawal request creation
      wrListInBytes = withGas(WithdrawalMsgProcessor.process(msgForListOfWR, view, _, blockContext))
      assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)
    }
  }

  def checkEvent(expectedEvent: AddWithdrawalRequest, actualEvent: EvmLog): Unit = {
    assertArrayEquals(
      "Wrong address",
      WithdrawalMsgProcessor.contractAddress,
      actualEvent.address.toBytes)
    // The first topic is the hash of the signature of the event
    assertEquals("Wrong number of topics", NumOfIndexedEvtParams + 1, actualEvent.topics.length)
    assertArrayEquals("Wrong event signature", AddNewWithdrawalRequestEventSig, actualEvent.topics(0).toBytes)
    assertEquals(
      "Wrong from address in topic",
      expectedEvent.from,
      decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.from.getTypeAsString)))
    assertEquals(
      "Wrong mcAddr in topic",
      expectedEvent.mcDest,
      decodeEventTopic(actualEvent.topics(2), TypeReference.makeTypeReference(expectedEvent.mcDest.getTypeAsString)))

    val listOfRefs = util.Arrays
      .asList(
        TypeReference.makeTypeReference(expectedEvent.value.getTypeAsString),
        TypeReference.makeTypeReference(expectedEvent.epochNumber.getTypeAsString))
      .asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong amount in data", expectedEvent.value, listOfDecodedData.get(0))
    assertEquals("Wrong epoch number in data", expectedEvent.epochNumber, listOfDecodedData.get(1))
  }
}
