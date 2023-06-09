package io.horizen.account.sc2sc

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.state.{AccountStateView, ExecutionFailedException, MessageProcessorFixture}
import io.horizen.evm.Address
import io.horizen.fixtures.StoreFixture
import io.horizen.params.MainNetParams
import io.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert.{assertArrayEquals, assertFalse, assertTrue}
import org.junit.{Before, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.Assertions.assertThrows
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar

import java.math.BigInteger
import java.util
import scala.util.Random

class AbstractCrossChainMessageProcessorTest extends MockitoSugar
  with MessageProcessorFixture
  with StoreFixture
  with CrossChainMessageProcessorFixture {

  var mockStateView: AccountStateView = _

  @Before
  def setUp(): Unit = {
    mockStateView = mock[AccountStateView]
    Mockito
      .when(mockStateView.getGasTrackedView(any()))
      .thenReturn(mockStateView)
  }

  @Test
  def testInit(): Unit = {
    Mockito
      .when(mockStateView.addAccount(ArgumentMatchers.any[Address], ArgumentMatchers.any[Array[Byte]]))
      .thenAnswer(args => {
        assertArrayEquals("Different address expected.", CrossChainMessageProcessorTestImpl.contractAddress.toBytes, args.getArgument(0).asInstanceOf[Address].toBytes)
        assertArrayEquals("Different code expected.", CrossChainMessageProcessorTestImpl.contractCode, args.getArgument(1))
      })
    getMessageProcessorTestImpl(MainNetParams()).init(mockStateView)
  }

  @Test
  def testCanProcess(): Unit = {
    val msg = listOfCrosschainMessages(1)
    assertTrue(
      "Message listOfCrosschainMessages cannot be processed",
      getMessageProcessorTestImpl(MainNetParams()).canProcess(msg, mockStateView)
    )
    val wrongAddress =  new Address("0x25fdd51e73221f467b40946c97791a3e19799beb")
    val msgNotProcessable = getMessage(wrongAddress, BigInteger.ZERO, Array.emptyByteArray)
    assertFalse(
      "Message not for CrosschainMsgProcessor can be processed",
      getMessageProcessorTestImpl(MainNetParams()).canProcess(msgNotProcessable, mockStateView)
    )
  }

  @Test
  def testProcessWithWrongPArams(): Unit = {
    val value = BigInteger.valueOf(1000000000L) // 1 zenny and 1 wei
    // msgWithWrongFunctionCall processing should result in ExecutionFailed
    val data = BytesUtils.fromHexString("99")
    val msgWithWrongFunctionCall = getMessage(CrossChainMessageProcessorTestImpl.contractAddress, value, data)
    assertThrows[ExecutionFailedException] {
      withGas(getMessageProcessorTestImpl(MainNetParams()).process(msgWithWrongFunctionCall, mockStateView, _, defaultBlockContext))
    }
  }

  @Test
  def testGetListOfWithdrawalReqs(): Unit = {
    val proc : AbstractCrossChainMessageProcessor  = getMessageProcessorTestImpl(MainNetParams())

    usingView(proc) { view =>
      proc.init(view)
      val epochNum = 102
      // No messages
      val msg = listOfCrosschainMessages(epochNum)
      val counterKey = getMessageProcessorTestImpl(MainNetParams()).getMessageEpochCounterKey(epochNum)
      val numOfWithdrawalReqs = Bytes.concat(new Array[Byte](32 - Ints.BYTES), Ints.toByteArray(0))

      Mockito
        .when(mockStateView.getAccountStorage(ArgumentMatchers.any[Address], ArgumentMatchers.any[Array[Byte]]))
        .thenReturn(numOfWithdrawalReqs)

      var returnData = withGas(proc.process(msg, mockStateView, _, defaultBlockContext))
      val expectedListOfWR = new util.ArrayList[AccountCrossChainMessage]()
      assertArrayEquals(CrosschainMessagesListEncoder.encode(expectedListOfWR), returnData)

      // With 3 messages
      val numOfMessages = 3
      val numOfMessagesReqsInBytes =
        Bytes.concat(new Array[Byte](32 - Ints.BYTES), Ints.toByteArray(numOfMessages))
      Mockito
        .when(mockStateView.getAccountStorage(ArgumentMatchers.any[Address], ArgumentMatchers.any[Array[Byte]]))
        .thenReturn(numOfMessagesReqsInBytes)


      val mockMessageRequestsList = new util.HashMap[ByteArrayWrapper, Array[Byte]](numOfMessages)

      for (index <- 1 to numOfMessages) {
        val wr = AccountCrossChainMessage(
          1,
          Array.fill(20)(Random.nextInt().toByte),
          Array.fill(32)(Random.nextInt().toByte),
          Array.fill(20)(Random.nextInt().toByte),
          Array.fill(32)(Random.nextInt().toByte)
        )
        wr.encode()
        expectedListOfWR.add(wr)
        val key = proc.getMessageKey(epochNum, index)
        mockMessageRequestsList.put(new ByteArrayWrapper(key), wr.bytes)
      }

      Mockito
        .when(mockStateView.getAccountStorageBytes(ArgumentMatchers.any[Address], ArgumentMatchers.any[Array[Byte]]))
        .thenAnswer(answer => {
          val key: Array[Byte] = answer.getArgument(1)
          mockMessageRequestsList.get(new ByteArrayWrapper(key))
        })

      returnData = withGas(proc.process(msg, mockStateView, _, defaultBlockContext), 10000000)
      assertArrayEquals(CrosschainMessagesListEncoder.encode(expectedListOfWR), returnData)
    }
  }
}
