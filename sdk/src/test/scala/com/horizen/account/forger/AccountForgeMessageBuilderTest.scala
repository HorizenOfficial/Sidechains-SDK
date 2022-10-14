package com.horizen.account.forger

import com.horizen.SidechainTypes
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import org.junit.Assert.{assertArrayEquals, assertEquals, assertTrue}
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar

import java.math.BigInteger

class AccountForgeMessageBuilderTest
    extends MockitoSugar
    with MessageProcessorFixture
    with EthereumTransactionFixture {

  @Test
  def testConsistentStateAfterMissingMsgProcessorError(): Unit = {
    val blockContext = new BlockContext(
      Array.empty[Byte],
      1000,
      BigInteger.ZERO,
      10000000000L,
      11,
      2,
      3
    )

    usingView { stateView =>
      val transaction = createLegacyTransaction(
        BigInteger.TEN,
        gasLimit = BigInteger.valueOf(10000000)
      )
      val fromAddress = transaction.getFrom.address()
      stateView.addBalance(fromAddress, BigInteger.valueOf(1000000000))

      val forger = new AccountForgeMessageBuilder(null, null, null, false)
      val initialStateRoot = stateView.getIntermediateRoot
      val listOfTxs =
        Seq[SidechainTypes#SCAT](transaction.asInstanceOf[SidechainTypes#SCAT])

      val (_,appliedTxs,_) = forger.computeBlockInfo(
        stateView,
        listOfTxs,
        Seq.empty,
        blockContext,
        null
      )
      assertTrue(appliedTxs.isEmpty)

      val finalStateRoot = stateView.getIntermediateRoot
      assertArrayEquals(initialStateRoot, finalStateRoot)
    }
  }

  @Test
  def testConsistentStateAfterRandomException(): Unit = {

    class BuggyTransaction(th: EthereumTransaction)
      extends EthereumTransaction(th.getTransaction) {
      override def version(): Byte = throw new Exception()
    }

    val invalidTx = new BuggyTransaction(
      createLegacyTransaction(
        BigInteger.TEN,
        gasLimit = BigInteger.valueOf(10000000)
      )
    )

    val blockContext = new BlockContext(
      Array.empty[Byte],
      1000,
      BigInteger.ZERO,
      10000000000L,
      11,
      2,
      3
    )

    val mockMsgProcessor: MessageProcessor = setupMockMessageProcessor

    usingView(mockMsgProcessor) { stateView =>
      stateView.addBalance(
        invalidTx.getFrom.address(),
        BigInteger.valueOf(1000000000)
      )

      val forger = new AccountForgeMessageBuilder(null, null, null, false)
      val initialStateRoot = stateView.getIntermediateRoot
      val listOfTxs =
        List[SidechainTypes#SCAT](
          invalidTx.asInstanceOf[SidechainTypes#SCAT]
        )
      val (_, appliedTxs, _) = forger.computeBlockInfo(
        stateView,
        listOfTxs,
        Seq.empty,
        blockContext,
        null
      )
      assertTrue(appliedTxs.isEmpty)

      val finalStateRoot = stateView.getIntermediateRoot
      assertArrayEquals(initialStateRoot, finalStateRoot)
    }
  }

  @Test
  def testConsistentBlockGasAfterRandomException(): Unit = {

    class BuggyTransaction(th: EthereumTransaction)
      extends EthereumTransaction(th.getTransaction) {
      override def version(): Byte = throw new Exception()
    }

    val gasLimit = GasUtil.intrinsicGas(Array.empty[Byte], isContractCreation = true).add(BigInteger.TEN)
    val invalidTx = new BuggyTransaction(
      createLegacyTransaction(
        BigInteger.TEN,
        gasLimit = gasLimit
      )
    )

    val validTx = createLegacyTransaction(
      BigInteger.TWO,
      gasLimit = gasLimit
    )

    val blockContext = new BlockContext(
      Array.empty[Byte],
      1000,
      BigInteger.ZERO,
      gasLimit.longValueExact(),//Just enough for 1 tx
      11,
      2,
      3
    )

    val mockMsgProcessor: MessageProcessor = setupMockMessageProcessor

    usingView(mockMsgProcessor) { stateView =>
      stateView.addBalance(
        invalidTx.getFrom.address(),
        BigInteger.valueOf(1000000000)
      )
      stateView.addBalance(
        validTx.getFrom.address(),
        BigInteger.valueOf(1000000000)
      )

      val forger = new AccountForgeMessageBuilder(null, null, null, false)

      val listOfTxs =
        List[SidechainTypes#SCAT](
          invalidTx.asInstanceOf[SidechainTypes#SCAT],
          validTx.asInstanceOf[SidechainTypes#SCAT]
        )
      val (_, appliedTxs, _) = forger.computeBlockInfo(
        stateView,
        listOfTxs,
        Seq.empty,
        blockContext,
        null
      )
      assertEquals(1, appliedTxs.size)
      assertEquals(validTx.id(), appliedTxs(0).id)
    }
  }

  private def setupMockMessageProcessor = {
    val mockMsgProcessor = mock[MessageProcessor]
    Mockito
      .when(
        mockMsgProcessor.canProcess(
          ArgumentMatchers.any[Message],
          ArgumentMatchers.any[BaseAccountStateView]
        )
      )
      .thenReturn(true)
    Mockito
      .when(
        mockMsgProcessor.process(
          ArgumentMatchers.any[Message],
          ArgumentMatchers.any[BaseAccountStateView],
          ArgumentMatchers.any[GasPool],
          ArgumentMatchers.any[BlockContext]
        )
      )
      .thenReturn(Array.empty[Byte])
    mockMsgProcessor
  }
}
