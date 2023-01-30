package com.horizen.account.forger

import com.horizen.SidechainTypes
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.mempool.TransactionsByPriceAndNonceIter
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.evm.utils.Address
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
      Address.ZERO,
      1000,
      BigInteger.ZERO,
      10000000000L,
      11,
      2,
      3,
      1
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
      val listOfTxs = setupTransactionsByPriceAndNonce(
        Seq[SidechainTypes#SCAT](transaction.asInstanceOf[SidechainTypes#SCAT]))

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

    class BuggyTransaction(th: EthereumTransaction, sign : SignatureSecp256k1)
      extends EthereumTransaction(th, sign) {
      override def version(): Byte = throw new Exception()
    }

    val tmpTx = createLegacyTransaction(
      BigInteger.TEN,
      gasLimit = BigInteger.valueOf(10000000)
    )
    val invalidTx = new BuggyTransaction(tmpTx, tmpTx.getSignature)

    val blockContext = new BlockContext(
      Address.ZERO,
      1000,
      BigInteger.ZERO,
      10000000000L,
      11,
      2,
      3,
      1
    )

    val mockMsgProcessor: MessageProcessor = setupMockMessageProcessor

    usingView(mockMsgProcessor) { stateView =>
      stateView.addBalance(
        invalidTx.getFrom.address(),
        BigInteger.valueOf(1000000000)
      )

      val forger = new AccountForgeMessageBuilder(null, null, null, false)
      val initialStateRoot = stateView.getIntermediateRoot
      val listOfTxs = setupTransactionsByPriceAndNonce(
        List[SidechainTypes#SCAT](
          invalidTx.asInstanceOf[SidechainTypes#SCAT]
        ))
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

    class BuggyTransaction(th: EthereumTransaction, sign : SignatureSecp256k1)
      extends EthereumTransaction(th, sign) {
      override def version(): Byte = throw new Exception()
    }

    val gasLimit = GasUtil.intrinsicGas(Array.empty[Byte], isContractCreation = true).add(BigInteger.TEN)
    val tmpTx = createLegacyTransaction(
      BigInteger.TEN,
      gasLimit = gasLimit
    )
    val invalidTx = new BuggyTransaction(
      tmpTx, tmpTx.getSignature
    )

    val validTx = createLegacyTransaction(
      BigInteger.TWO,
      gasLimit = gasLimit
    )

    val blockContext = new BlockContext(
      Address.ZERO,
      1000,
      BigInteger.ZERO,
      gasLimit.longValueExact(),//Just enough for 1 tx
      11,
      2,
      3,
      1
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

      val listOfTxs = setupTransactionsByPriceAndNonce(
        List[SidechainTypes#SCAT](
          invalidTx.asInstanceOf[SidechainTypes#SCAT],
          validTx.asInstanceOf[SidechainTypes#SCAT]
        ))
      val (_, appliedTxs, _) = forger.computeBlockInfo(
        stateView,
        listOfTxs,
        Seq.empty,
        blockContext,
        null
      )
      assertEquals(1, appliedTxs.size)
      assertEquals(validTx.id(), appliedTxs.head.id)
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

  private def setupTransactionsByPriceAndNonce(listOfTxs: Seq[SidechainTypes#SCAT]): Iterable[SidechainTypes#SCAT] = {
    val txsByPriceAndNonceIter: TransactionsByPriceAndNonceIter = new TransactionsByPriceAndNonceIter {
      var idx = 0
      override def peek(): SidechainTypes#SCAT = listOfTxs(idx)

      override def removeAndSkipAccount(): SidechainTypes#SCAT = {
        next()
      }

      override def hasNext: Boolean = idx < listOfTxs.size

      override def next(): SidechainTypes#SCAT =  {
        val curr = listOfTxs(idx)
        idx = idx + 1
        curr
      }
    }
    val txsByPriceAndNonce : Iterable[SidechainTypes#SCAT] = mock[Iterable[SidechainTypes#SCAT]]
    Mockito.when(txsByPriceAndNonce.iterator).thenReturn(txsByPriceAndNonceIter)
    txsByPriceAndNonce
  }
}
