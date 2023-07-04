package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.state.events.AddWithdrawalRequest
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.evm.{Address, Hash}
import io.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert._
import org.junit._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import sparkz.crypto.hash.Keccak256

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
      WithdrawalMsgProcessor.init(view, 0)
      assertTrue("Account doesn't exist after init", view.accountExists(address))
      assertEquals("Wrong initial balance", BigInteger.ZERO, view.getBalance(address))
      assertEquals("Wrong initial nonce", BigInteger.ZERO, view.getNonce(address))
      assertArrayEquals(
        "Wrong initial code hash",
        WithdrawalMsgProcessor.contractCodeHash,
        view.getCodeHash(address)
      )
      assertTrue(view.isSmartContractAccount(address))
    }
  }

  @Test
  def testWithdrawalRequestProcessorIntegration(): Unit = {
    usingView(WithdrawalMsgProcessor) { view =>
      WithdrawalMsgProcessor.init(view, 0)

      val withdrawalEpoch = 102
      val blockContext = new BlockContext(
        Address.ZERO,
        0,
        0,
        DefaultGasFeeFork.blockGasLimit,
        0,
        0,
        withdrawalEpoch,
        1,
        MockedHistoryBlockHashProvider,
        Hash.ZERO
      )

      // GetListOfWithdrawalRequest without withdrawal requests yet
      val msgForListOfWR = listWithdrawalRequestsMessage(withdrawalEpoch)
      var wrListInBytes = assertGas(2100, msgForListOfWR, view, WithdrawalMsgProcessor, blockContext)
      val expectedListOfWR = new util.ArrayList[WithdrawalRequest]()
      assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)

      // Invalid request for withdrawal amount under dust threshold
      view.subBalance(msgForListOfWR.getFrom, view.getBalance(msgForListOfWR.getFrom))
      val withdrawalAmount = ZenWeiConverter.convertZenniesToWei(10)
      val msgBalance = addWithdrawalRequestMessage(withdrawalAmount)
      // Withdrawal request with amount under dust threshold should result in ExecutionRevertedException
      assertThrows[ExecutionRevertedException] {
        assertGas(0, msgBalance, view, WithdrawalMsgProcessor, blockContext)
      }

      // Creating the first Withdrawal request
      val withdrawalAmount1 = ZenWeiConverter.convertZenniesToWei(123)
      var msg = addWithdrawalRequestMessage(withdrawalAmount1)
      val initialBalance = ZenWeiConverter.convertZenniesToWei(1300)
      view.addBalance(msg.getFrom, initialBalance)
      var newExpectedWR = WithdrawalRequest(mcAddr, msg.getValue)
      expectedListOfWR.add(newExpectedWR)

      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val wrInBytes = assertGas(68412, msg, view, WithdrawalMsgProcessor, blockContext)
      assertArrayEquals(newExpectedWR.encode(), wrInBytes)
      val newBalance = view.getBalance(msg.getFrom)
      assertEquals("Wrong value in account balance", 1177, ZenWeiConverter.convertWeiToZennies(newBalance))

      // Checking log
      var listOfLogs = view.getLogs(txHash1.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      var expectedEvent = AddWithdrawalRequest(msg.getFrom, mcAddr, withdrawalAmount1, withdrawalEpoch)
      checkEvent(expectedEvent, listOfLogs(0))

      val txHash2 = Keccak256.hash("second tx")
      view.setupTxContext(txHash2, 10)

      // Negative test: check we have an exception if using a request with bytes which not decode (epoch number not an Uint32)
      val badMsgGetList = {
        val params = BytesUtils.fromHexString("46ed7a8f0346ff7e3125646e7ef1e83dcf4af172c69a37550f5c250da430ec04")
        val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig), params)
        getMessage(WithdrawalMsgProcessor.contractAddress, BigInteger.ZERO, data)
      }
      val exGetList = intercept[Throwable] {
         assertGas(0, badMsgGetList, view, WithdrawalMsgProcessor, defaultBlockContext)
      }
      assertTrue(exGetList.getMessage.contains("Could not decode"))

      // GetListOfWithdrawalRequest after first withdrawal request creation
      wrListInBytes = assertGas(6300, msgForListOfWR, view, WithdrawalMsgProcessor, blockContext)
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

      val wrInBytes2 = assertGas(48512, msg, view, WithdrawalMsgProcessor, blockContext)
      assertArrayEquals(newExpectedWR.encode(), wrInBytes2)

      val newBalanceAfterSecondWR = view.getBalance(msg.getFrom)
      val expectedBalance = newBalance.subtract(withdrawalAmount2)
      assertEquals("Wrong value in account balance", expectedBalance, newBalanceAfterSecondWR)

      // Checking log
      listOfLogs = view.getLogs(txHash3.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      expectedEvent = AddWithdrawalRequest(msg.getFrom, mcAddr, withdrawalAmount2, withdrawalEpoch)
      checkEvent(expectedEvent, listOfLogs(0))

      // GetListOfWithdrawalRequest after second withdrawal request creation
      wrListInBytes = assertGas(10500, msgForListOfWR, view, WithdrawalMsgProcessor, blockContext)
      assertArrayEquals(WithdrawalRequestsListEncoder.encode(expectedListOfWR), wrListInBytes)

      // Negative test: check we have an exception if using a request with trailing bytes in the input params
      val badMsg = {
        val params = Bytes.concat(AddWithdrawalRequestCmdInput(mcAddr).encode(), new Array[Byte](1))
        val data = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig), params)
        getMessage(WithdrawalMsgProcessor.contractAddress, withdrawalAmount2, data)
      }
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, badMsg, view, WithdrawalMsgProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))
    }
  }

  def checkEvent(expectedEvent: AddWithdrawalRequest, actualEvent: EthereumConsensusDataLog): Unit = {
    assertEquals(
      "Wrong address",
      WithdrawalMsgProcessor.contractAddress,
      actualEvent.address
    )
    // The first topic is the hash of the signature of the event
    assertEquals("Wrong number of topics", NumOfIndexedEvtParams + 1, actualEvent.topics.length)
    assertArrayEquals("Wrong event signature", AddNewWithdrawalRequestEventSig, actualEvent.topics(0).toBytes)
    assertEquals(
      "Wrong from address in topic",
      expectedEvent.from,
      decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.from.getTypeAsString))
    )
    assertEquals(
      "Wrong mcAddr in topic",
      expectedEvent.mcDest,
      decodeEventTopic(actualEvent.topics(2), TypeReference.makeTypeReference(expectedEvent.mcDest.getTypeAsString))
    )

    val listOfRefs = util.Arrays
      .asList(
        TypeReference.makeTypeReference(expectedEvent.value.getTypeAsString),
        TypeReference.makeTypeReference(expectedEvent.epochNumber.getTypeAsString)
      )
      .asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong amount in data", expectedEvent.value, listOfDecodedData.get(0))
    assertEquals("Wrong epoch number in data", expectedEvent.epochNumber, listOfDecodedData.get(1))
  }
}
