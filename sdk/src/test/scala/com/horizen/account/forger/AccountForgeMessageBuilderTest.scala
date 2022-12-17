package com.horizen.account.forger

import com.horizen.account.block.AccountBlockHeader
import com.horizen.{SidechainTypes, SidechainWallet}
import com.horizen.account.fixtures.EthereumTransactionFixture
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.mempool.TransactionsByPriceAndNonceIter
import com.horizen.account.secret.PrivateKeySecp256k1
import com.horizen.account.state._
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.block.{MainchainBlockReferenceData, MainchainHeader, Ommer}
import com.horizen.chain.MainchainHeaderHash
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.secret.PrivateKey25519
import com.horizen.transaction.TransactionSerializer
import com.horizen.utils.{DynamicTypedSerializer, MerklePath}
import org.junit.Assert.{assertArrayEquals, assertEquals, assertTrue}
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.Assertions.assertThrows
import org.scalatestplus.mockito.MockitoSugar
import scorex.util.ModifierId
import sparkz.core.block.Block.BlockId
import sparkz.core.transaction.state.Secret

import java.math.BigInteger
import java.util

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
      3,
      1
    )

    usingView { stateView =>
      val transaction = createLegacyTransaction(
        BigInteger.TEN,
        gasLimit = BigInteger.valueOf(10000000)
      )
      val fromAddress = transaction.getFrom.address()
      stateView.addBalance(fromAddress, BigInteger.valueOf(100000000010L))

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
      Array.empty[Byte],
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
        BigInteger.valueOf(100000000010L)
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
      override def version(): Byte = throw new Exception("Transaction %s failed to execute".format(th.id()))
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
      Array.empty[Byte],
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

  @Test
  def testCreateNewBlockFailingIfAddressSizeIsZero(): Unit = {
    val nodeView = mock[forger.View]
    Mockito.when(nodeView.vault.secretsOfType(classOf[PrivateKeySecp256k1]))
      .thenReturn(List.empty[Secret])

    val branchPointInfo = mock[forger.BranchPointInfo]
    val parentId = mock[BlockId]
    val mainchainBlockReferencesData = Seq(mock[MainchainBlockReferenceData])
    val sidechainTransactions = mock[Iterable[SidechainTypes#SCAT]]
    val mainchainHeaders = Seq(mock[MainchainHeader])
    val ommers = Seq(mock[Ommer[AccountBlockHeader]])
    val ownerPrivateKey = mock[PrivateKey25519]
    val forgingStakeInfo = mock[ForgingStakeInfo]
    val vrfProof = mock[VrfProof]
    val forgingStakeInfoMerklePath = mock[MerklePath]
    val companion = mock[DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]]]
    val inputBlockSize = 0
    val signatureOption = Some(mock[Signature25519])

    val forger = new AccountForgeMessageBuilder(null, null, null, false)

    assertThrows[IllegalArgumentException](classOf[IllegalArgumentException], forger.createNewBlock(
      nodeView,
      branchPointInfo,
      false,
      parentId,
      0L,
      mainchainBlockReferencesData,
      sidechainTransactions,
      mainchainHeaders,
      ommers,
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      forgingStakeInfoMerklePath,
      companion,
      inputBlockSize,
      signatureOption))
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
