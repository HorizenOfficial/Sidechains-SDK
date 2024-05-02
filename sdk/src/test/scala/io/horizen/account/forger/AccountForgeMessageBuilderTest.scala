package io.horizen.account.forger

import io.horizen.account.block.AccountBlockHeader
import io.horizen.account.fixtures.EthereumTransactionFixture
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.{AccountMemoryPool, TransactionsByPriceAndNonceIter}
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import io.horizen.account.state._
import io.horizen.account.state.receipt.{EthereumReceipt, ReceiptFixture}
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.transaction.EthereumTransaction.EthereumTransactionType
import io.horizen.account.utils.{AccountMockDataHelper, EthereumTransactionEncoder, FeeUtils, WellKnownAddresses, ZenWeiConverter}
import io.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData, MainchainHeader, Ommer}
import io.horizen.chain.SidechainBlockInfo
import io.horizen.consensus.{ConsensusParamsUtil, ForgingStakeInfo}
import io.horizen.evm.{Address, Hash}
import io.horizen.fixtures.{CompanionsFixture, SecretFixture, SidechainRelatedMainchainOutputFixture, VrfGenerator}
import io.horizen.fork.{ConsensusParamsFork, ConsensusParamsForkInfo, CustomForkConfiguratorWithConsensusParamsFork, ForkManagerUtil}
import io.horizen.metrics.MetricsManager
import io.horizen.params.{NetworkParams, TestNetParams}
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.VrfPublicKey
import io.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import io.horizen.state.BaseStateReader
import io.horizen.transaction.{MC2SCAggregatedTransaction, TransactionSerializer}
import io.horizen.utils.{BytesUtils, DynamicTypedSerializer, MerklePath, Pair, TestSidechainsVersionsManager, TimeToEpochUtils, WithdrawalEpochInfo}
import io.horizen.vrf.VrfOutput
import io.horizen.{AccountMempoolSettings, SidechainTypes}
import org.junit.Assert.{assertArrayEquals, assertEquals, assertTrue}
import org.junit.{Before, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.Assertions.assertThrows
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.utils.Numeric
import sparkz.core.transaction.state.Secret
import sparkz.core.utils.NetworkTimeProvider
import sparkz.crypto.hash.Keccak256
import sparkz.util.serialization.VLQByteBufferWriter
import sparkz.util.{ByteArrayBuilder, bytesToId}

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util
import java.util.Optional
import scala.io.Source
import scala.collection.JavaConverters._

class AccountForgeMessageBuilderTest
    extends MockitoSugar
      with MessageProcessorFixture
      with EthereumTransactionFixture
      with SidechainTypes
      with ReceiptFixture
      with CompanionsFixture
      with SecretFixture
      with SidechainRelatedMainchainOutputFixture {


  @Before
  def init(): Unit = {
    MetricsManager.init(mock[NetworkTimeProvider])
    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(), Seq(), Seq()), "regtest")
  }

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
      1,
      MockedHistoryBlockHashProvider,
      Hash.ZERO
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
        Seq[SidechainTypes#SCAT](transaction.asInstanceOf[SidechainTypes#SCAT])
      )

      val inputBlockSize = 100L
      val (_, appliedTxs, _) = forger.computeBlockInfo(
        stateView,
        listOfTxs,
        Seq.empty,
        blockContext,
        null,
        inputBlockSize
      )
      assertTrue(appliedTxs.isEmpty)

      val finalStateRoot = stateView.getIntermediateRoot
      assertArrayEquals(initialStateRoot, finalStateRoot)
    }
  }

  @Test
  def testConsistentStateAfterRandomException(): Unit = {
    val invalidTx = createLegacyTransaction(BigInteger.TEN, gasLimit = 25000)
    val blockContext = new BlockContext(
      Address.ZERO,
      1000,
      BigInteger.ZERO,
      10000000000L,
      11,
      2,
      3,
      1,
      MockedHistoryBlockHashProvider,
      Hash.ZERO
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
        )
      )
      val inputBlockSize = 100L
      val (_, appliedTxs, _) = forger.computeBlockInfo(
        stateView,
        listOfTxs,
        Seq.empty,
        blockContext,
        null,
        inputBlockSize
      )
      assertTrue(appliedTxs.isEmpty)

      val finalStateRoot = stateView.getIntermediateRoot
      assertArrayEquals(initialStateRoot, finalStateRoot)
    }
  }

  @Test
  def testConsistentBlockGasAfterRandomException(): Unit = {
    val invalidTx = createLegacyTransaction(BigInteger.TEN, gasLimit = 25000)
    val validTx = createLegacyTransaction(BigInteger.TWO, gasLimit = 25000)

    val blockContext = new BlockContext(
      Address.ZERO,
      1000,
      BigInteger.ZERO,
      30000, // just enough for 1 tx
      11,
      2,
      3,
      1,
      MockedHistoryBlockHashProvider,
      Hash.ZERO
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
        )
      )
      val inputBlockSize = 100L
      val (_, appliedTxs, _) = forger.computeBlockInfo(
        stateView,
        listOfTxs,
        Seq.empty,
        blockContext,
        null,
        inputBlockSize
      )
      assertEquals(1, appliedTxs.size)
      assertEquals(validTx.id(), appliedTxs.head.id)
    }
  }

  @Test
  def testCreateNewBlockFailingIfAddressSizeIsZero(): Unit = {
    val nodeView = mock[forger.View]
    val vlMock = mock[forger.VL]
    val secrets = new java.util.ArrayList[Secret]()

    Mockito.when(nodeView.vault).thenReturn(vlMock)
    Mockito.when(vlMock.secretsOfType(classOf[PrivateKeySecp256k1])).thenAnswer(_ => secrets)

    val branchPointInfo = mock[forger.BranchPointInfo]
    val mainchainBlockReferencesData = Seq(mock[MainchainBlockReferenceData])
    val sidechainTransactions = mock[Iterable[SidechainTypes#SCAT]]
    val mainchainHeaders = Seq(mock[MainchainHeader])
    val ommers = Seq(mock[Ommer[AccountBlockHeader]])
    val ownerPrivateKey = mock[PrivateKey25519]
    val forgingStakeInfo = mock[ForgingStakeInfo]
    val vrfProof = mock[VrfProof]
    val vrfOutput = mock[VrfOutput]
    val forgingStakeInfoMerklePath = mock[MerklePath]
    val companion = mock[DynamicTypedSerializer[SidechainTypes#SCAT, TransactionSerializer[SidechainTypes#SCAT]]]
    val inputBlockSize = 0
    val signatureOption = Some(mock[Signature25519])
    val params = mock[NetworkParams]
    when(params.rewardAddress).thenReturn(Option.empty)

    val forger = new AccountForgeMessageBuilder(null, null, params, false)

    assertThrows[IllegalArgumentException](
      classOf[IllegalArgumentException],
      forger.createNewBlock(
        nodeView,
        branchPointInfo,
        isWithdrawalEpochLastBlock = false,
        null,
        0L,
        mainchainBlockReferencesData,
        sidechainTransactions,
        mainchainHeaders,
        ommers,
        ownerPrivateKey,
        forgingStakeInfo,
        vrfProof,
        vrfOutput,
        forgingStakeInfoMerklePath,
        companion,
        inputBlockSize,
        signatureOption
      )
    )
  }

  @Test
  def testCreateNewBlockSuccessful(): Unit = {
    val nodeView = mock[forger.View]
    val vlMock = mock[forger.VL]
    val secretsMock = mock[java.util.List[Secret]]
    val fittingSecret: PrivateKeySecp256k1 = getPrivateKeySecp256k1(10344)

    Mockito.when(secretsMock.size()).thenReturn(0)
    Mockito.when(nodeView.vault).thenReturn(vlMock)
    Mockito.when(vlMock.secretsOfType(classOf[PrivateKeySecp256k1])).thenAnswer(_ => util.Arrays.asList(fittingSecret))

    Mockito.when(nodeView.history).thenReturn(mock[AccountHistory])
    val epochSizeInSlots = 15
    val slotLengthInSeconds = 20
    val totalBlockCount = epochSizeInSlots * 4
    val genesisTimestamp: Long = Instant.now.getEpochSecond - (slotLengthInSeconds * totalBlockCount)
    val params = TestNetParams(
      sidechainGenesisBlockTimestamp = genesisTimestamp
    )
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(epochSizeInSlots, slotLengthInSeconds)),
    ))
    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(TimeToEpochUtils.virtualGenesisBlockTimeStamp(params.sidechainGenesisBlockTimestamp)))
    Mockito.when(nodeView.history.params).thenReturn(params)
    Mockito.when(nodeView.history.modifierById(any())).thenReturn(None)

    val epochInfoWE0 = WithdrawalEpochInfo(epoch = 0, lastEpochIndex = 1)
    Mockito.when(nodeView.history.blockInfoById(any())).thenAnswer(_ => {
      val blockInfo: SidechainBlockInfo = mock[SidechainBlockInfo]
      Mockito.when(blockInfo.withdrawalEpochInfo).thenAnswer(_ => epochInfoWE0)
      blockInfo
    })

    val receipt: EthereumReceipt =
      createTestEthereumReceipt(
        EthereumTransactionType.DynamicFeeTxType.ordinal(),
        transactionIndex = 0,
        blockNumber = 2,
        address = new Address("0xd2a538a476aad6ecd245099df9297df6a129c2c5"),
        txHash = Some(BytesUtils.fromHexString("6411db6b0b891abd9bd970562f71d4bd69b1ee3359d627c98856f024dec16253")),
        blockHash = "0456"
      )

    val goodSignature = new SignatureSecp256k1(
      new BigInteger("1c", 16),
      new BigInteger("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023", 16),
      new BigInteger("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d", 16)
    )
    val txEip1559 = new EthereumTransaction(
      params.chainId,
      Optional.empty[AddressProposition],
      BigInteger.valueOf(0L),
      BigInteger.valueOf(1),
      BigInteger.valueOf(1),
      FeeUtils.INITIAL_BASE_FEE.add(BigInteger.valueOf(100)),
      BigInteger.valueOf(1),
      new Array[Byte](0),
      goodSignature
    )
    val writer = new VLQByteBufferWriter(new ByteArrayBuilder)
    EthereumTransactionEncoder.encodeAsRlpValues(txEip1559, txEip1559.isSigned, writer)
    val encodedMessage = writer.toBytes
    val txHash = BytesUtils.toHexString(Keccak256.hash(encodedMessage))
    val mockedState: AccountState =
      AccountMockDataHelper(false).getMockedState(receipt, Numeric.hexStringToByteArray(txHash))

    Mockito.when(nodeView.state).thenReturn(mockedState)

    val branchPointInfo = mock[forger.BranchPointInfo]
    val mcBlockRef1: MainchainBlockReference = MainchainBlockReference.create(
      BytesUtils.fromHexString(Source.fromResource("mcblock473173_mainnet").getLines().next()),
      params,
      TestSidechainsVersionsManager()
    ).get
    val mainchainBlockReferencesData = Seq(mcBlockRef1.data)
    val mainchainHeaders = Seq(mcBlockRef1.header)

    val initialStateNonce = BigInteger.ZERO
    val accountStateViewMock = mock[AccountStateReader]
    val baseStateViewMock = mock[BaseStateReader]
    Mockito.when(baseStateViewMock.getNextBaseFee).thenReturn(BigInteger.ZERO)
    Mockito.when(accountStateViewMock.getNonce(ArgumentMatchers.any[Address])).thenReturn(initialStateNonce)

    val accountMemoryPool = AccountMemoryPool.createEmptyMempool(() => accountStateViewMock,
      () => baseStateViewMock, AccountMempoolSettings(), () => mock[AccountEventNotifier])

    // Adding some txs in the mempool

    val value = BigInteger.TEN
    val account1Key: PrivateKeySecp256k1 =
      PrivateKeySecp256k1Creator.getInstance().generateSecret("mempooltest1".getBytes(StandardCharsets.UTF_8))

    val account1ExecTransaction0 = createEIP1559Transaction(
      value,
      initialStateNonce,
      Option(account1Key),
      gasFee = BigInteger.valueOf(3),
      priorityGasFee = BigInteger.valueOf(3)
    )
    accountMemoryPool.put(account1ExecTransaction0)

    val sidechainTransactions = accountMemoryPool.takeExecutableTxs()

    val ommers = Seq()
    val ownerPrivateKey = PrivateKey25519Creator.getInstance().generateSecret("KeySeed".getBytes(StandardCharsets.UTF_8))

    val proofAndOutput = VrfGenerator.generateProofAndOutput(123)
    val vrfProof = proofAndOutput.getKey
    val vrfOutput = proofAndOutput.getValue
    val forgingStakeInfo =
      new ForgingStakeInfo(ownerPrivateKey.publicImage(), new VrfPublicKey(new Array[Byte](VrfPublicKey.KEY_LENGTH)), 1)
    val forgingStakeInfoMerklePath = new MerklePath(new util.ArrayList[Pair[java.lang.Byte, Array[Byte]]])
    val companion = getDefaultAccountTransactionsCompanion
    val inputBlockSize = 1
    val signatureOption = Some(getRandomSignature25519)

    val forger = new AccountForgeMessageBuilder(null, companion, params, false)

    val block = forger.createNewBlock(
      nodeView,
      branchPointInfo,
      isWithdrawalEpochLastBlock = false,
      bytesToId(mcBlockRef1.header.bytes),
      genesisTimestamp,
      mainchainBlockReferencesData,
      sidechainTransactions,
      mainchainHeaders,
      ommers,
      ownerPrivateKey,
      forgingStakeInfo,
      vrfProof,
      vrfOutput,
      forgingStakeInfoMerklePath,
      companion,
      inputBlockSize,
      signatureOption
    )
    assertTrue("Could not forge block", block.isSuccess)
  }

  private def setupMockMessageProcessor = {
    val mockMsgProcessor = mock[MessageProcessor]
    Mockito
      .when(
        mockMsgProcessor.canProcess(
          ArgumentMatchers.any[Invocation],
          ArgumentMatchers.any[AccountStateView],
          ArgumentMatchers.any[Int]
        )
      )
      .thenReturn(true)
    Mockito
      .when(
        mockMsgProcessor.process(
          ArgumentMatchers.any[Invocation],
          ArgumentMatchers.any[BaseAccountStateView],
          ArgumentMatchers.any[ExecutionContext]
        )
      )
      .thenThrow(new RuntimeException("kaputt"))
      .thenReturn(Array.empty[Byte])
    mockMsgProcessor
  }

  private def setupTransactionsByPriceAndNonce(listOfTxs: Seq[SidechainTypes#SCAT]): Iterable[SidechainTypes#SCAT] = {
    val txsByPriceAndNonceIter: TransactionsByPriceAndNonceIter = new TransactionsByPriceAndNonceIter {
      var idx = 0
      override def peek: SidechainTypes#SCAT = listOfTxs(idx)

      override def removeAndSkipAccount(): SidechainTypes#SCAT = {
        next()
      }

      override def hasNext: Boolean = idx < listOfTxs.size

      override def next(): SidechainTypes#SCAT = {
        val curr = listOfTxs(idx)
        idx = idx + 1
        curr
      }
    }
    val txsByPriceAndNonce: Iterable[SidechainTypes#SCAT] = mock[Iterable[SidechainTypes#SCAT]]
    Mockito.when(txsByPriceAndNonce.iterator).thenReturn(txsByPriceAndNonceIter)
    txsByPriceAndNonce
  }
}
