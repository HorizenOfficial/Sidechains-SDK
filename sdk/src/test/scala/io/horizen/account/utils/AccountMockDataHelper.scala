package io.horizen.account.utils

import io.horizen.SidechainTypes
import io.horizen.account.api.rpc.types.EthereumTransactionView
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.sc2sc.{ScTxCommitmentTreeRootHashMessageProcessor, ScTxCommitmentTreeRootHashMessageProvider}
import io.horizen.account.secret.PrivateKeySecp256k1
import io.horizen.account.state._
import io.horizen.account.state.receipt.EthereumReceipt
import io.horizen.account.storage.AccountStateMetadataStorageView
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.wallet.AccountWallet
import io.horizen.block.SidechainBlockBase.GENESIS_BLOCK_PARENT_ID
import io.horizen.block.{MainchainBlockReference, MainchainBlockReferenceData}
import io.horizen.chain.{MainchainHeaderBaseInfo, MainchainHeaderInfo, SidechainBlockInfo}
import io.horizen.companion.SidechainSecretsCompanion
import io.horizen.cryptolibprovider.utils.FieldElementUtils
import io.horizen.customtypes.{CustomPrivateKey, CustomPrivateKeySerializer}
import io.horizen.evm.results.ProofAccountResult
import io.horizen.evm.{Address, Hash, StateDB}
import io.horizen.fixtures.SidechainBlockFixture.{generateMainchainBlockReference, generateMainchainHeaderHash}
import io.horizen.fixtures.{FieldElementFixture, SidechainRelatedMainchainOutputFixture, StoreFixture, VrfGenerator}
import io.horizen.params.{MainNetParams, NetworkParams, RegTestParams}
import io.horizen.proposition.Proposition
import io.horizen.secret.{Secret, SecretSerializer}
import io.horizen.storage.{SidechainSecretStorage, Storage}
import io.horizen.transaction.MC2SCAggregatedTransaction
import io.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import io.horizen.utils.{ByteArrayWrapper, BytesUtils, MerkleTree, Pair, WithdrawalEpochInfo}
import io.horizen.utxo.box.Box
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.utils.Numeric
import sparkz.core.consensus.ModifierSemanticValidity
import sparkz.util.{ModifierId, bytesToId}

import java.lang.{Byte => JByte}
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.{Optional, HashMap => JHashMap}
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}

case class AccountMockDataHelper(genesis: Boolean)
    extends JUnitSuite
      with StoreFixture
      with SidechainRelatedMainchainOutputFixture
      with MessageProcessorFixture {

  def getMockedAccoutMemoryPool: AccountMemoryPool = {
    val memoryPool: AccountMemoryPool = mock[AccountMemoryPool]

    // executable transaction IDs list
    val executableTxsIdList: Iterable[ModifierId] =
      List(bytesToId(new Array[Byte](32)), bytesToId(new Array[Byte](32)), bytesToId(new Array[Byte](32)))
    Mockito.when(memoryPool.getExecutableTransactions).thenReturn(executableTxsIdList.toList.asJava)

    // non executable transaction IDs list
    val nonExecutableTxsIdList: Iterable[ModifierId] = List(bytesToId(new Array[Byte](32)))
    Mockito.when(memoryPool.getNonExecutableTransactions).thenReturn(nonExecutableTxsIdList.toList.asJava)

    // latest nonce of account
    Mockito.when(memoryPool.getPoolNonce(any[SidechainTypes#SCP])).thenReturn(BigInteger.ZERO)

    // default signature for all txs
    val defaultSignature = new SignatureSecp256k1(
      new BigInteger("1c", 16),
      new BigInteger("805c658ac084be6da079d96bd4799bef3aa4578c8e57b97c3c6df9f581551023", 16),
      new BigInteger("568277f09a64771f5b4588ff07f75725a8e40d2c641946eb645152dcd4c93f0d", 16)
    )

    // executable address/nonces/transactions map
    val executableTxsMap = TrieMap.empty[Address, mutable.SortedMap[BigInteger, EthereumTransactionView]]
    // executable address/nonces/transactions map from a single address
    val executableTxsMapFrom = TrieMap.empty[Address, mutable.SortedMap[BigInteger, EthereumTransactionView]]
    // executable address/nonces/inspect map
    val executableTxsMapInspect = TrieMap.empty[Address, mutable.SortedMap[BigInteger, String]]

    // proposition 1 executable transactions
    val executableNonceTxsMap1: mutable.TreeMap[BigInteger, EthereumTransactionView] =
      new mutable.TreeMap[BigInteger, EthereumTransactionView]()
    // proposition 1 executable transactions inspect
    val executableNonceTxsMapInspect1: mutable.TreeMap[BigInteger, String] =
      new mutable.TreeMap[BigInteger, String]()

    val proposition1 = new AddressProposition(BytesUtils.fromHexString("15532e34426cd5c37371ff455a5ba07501c0f522"))
    val toProposition = new AddressProposition(BytesUtils.fromHexString("15532e34426cd5c37371ff455a5ba07501c0f522"))

    val executableTx1 = new EthereumTransaction(
      1997L,
      Optional.of(toProposition),
      BigInteger.valueOf(16L),
      BigInteger.valueOf(15467876L),
      BigInteger.valueOf(454545L),
      FeeUtils.INITIAL_BASE_FEE.add(BigInteger.valueOf(100)),
      BigInteger.valueOf(15000000L),
      BytesUtils.fromHexString("bd54d1f34e34a90f7dc5efe0b3d65fa4"),
      defaultSignature
    )
    val executableTx2 = new EthereumTransaction(
      1997L,
      Optional.of(toProposition),
      BigInteger.valueOf(24L),
      BigInteger.valueOf(15467876L),
      BigInteger.valueOf(454545L),
      FeeUtils.INITIAL_BASE_FEE.add(BigInteger.valueOf(100)),
      BigInteger.valueOf(4800000L),
      BytesUtils.fromHexString("8c64fe48688ab096dfb6ac2eeefcf213"),
      defaultSignature
    )

    val executableTxView1 = new EthereumTransactionView(executableTx1)
    val executableTxView2 = new EthereumTransactionView(executableTx2)

    executableNonceTxsMap1.put(BigInteger.valueOf(16), executableTxView1)
    executableNonceTxsMap1.put(BigInteger.valueOf(24), executableTxView2)
    executableTxsMap.put(proposition1.address(), executableNonceTxsMap1)
    executableTxsMapFrom.put(proposition1.address(), executableNonceTxsMap1)

    executableNonceTxsMapInspect1.put(BigInteger.valueOf(16), "0x15532e34426cd5c37371ff455a5ba07501c0f522: 15000000 wei + 15467876 gas × 1000000100 wei")
    executableNonceTxsMapInspect1.put(BigInteger.valueOf(24), "0x15532e34426cd5c37371ff455a5ba07501c0f522: 4800000 wei + 15467876 gas × 1000000100 wei")
    executableTxsMapInspect.put(proposition1.address(), executableNonceTxsMapInspect1)

    // proposition 2 executable transactions
    val executableNonceTxsMap2: mutable.TreeMap[BigInteger, EthereumTransactionView] =
      new mutable.TreeMap[BigInteger, EthereumTransactionView]()
    // proposition 2 executable transactions inspect
    val executableNonceTxsMapInspect2: mutable.TreeMap[BigInteger, String] =
      new mutable.TreeMap[BigInteger, String]()

    val proposition2 = new AddressProposition(BytesUtils.fromHexString("b039865dbea73df08e23f185847bab8e6a44108d"))

    val executableTx3 = new EthereumTransaction(
      1997L,
      Optional.of(toProposition),
      BigInteger.valueOf(32L),
      BigInteger.valueOf(15467876L),
      BigInteger.valueOf(454545L),
      FeeUtils.INITIAL_BASE_FEE.add(BigInteger.valueOf(100)),
      BigInteger.valueOf(18000000L),
      BytesUtils.fromHexString("bd54d1f34e34a90f7dc5efe0b3d65fa4"),
      defaultSignature
    )

    val executableTxView3 = new EthereumTransactionView(executableTx3)

    executableNonceTxsMap2.put(BigInteger.valueOf(32), executableTxView3)
    executableTxsMap.put(proposition2.address(), executableNonceTxsMap2)

    executableNonceTxsMapInspect2.put(BigInteger.valueOf(32), "0x15532e34426cd5c37371ff455a5ba07501c0f522: 18000000 wei + 15467876 gas × 1000000100 wei")
    executableTxsMapInspect.put(proposition2.address(), executableNonceTxsMapInspect2)

    // mock getExecutableTransactionsMap method call
    Mockito.when(memoryPool.getExecutableTransactionsMap).thenReturn(executableTxsMap)
    // mock getExecutableTransactionsMapFrom method call
    Mockito.when(memoryPool.getExecutableTransactionsMapFrom(any[Address])).thenReturn(executableNonceTxsMap1)
    // mock getExecutableTransactionsMapInspect method call
    Mockito.when(memoryPool.getExecutableTransactionsMapInspect).thenReturn(executableTxsMapInspect)

    // ----------------------------------------------------------------------------------------------------
    // non executable address/nonces/transactions map
    val nonExecutableTxsMap = TrieMap.empty[Address, mutable.SortedMap[BigInteger, EthereumTransactionView]]
    // non executable address/nonces/transactions map from a single address
    val nonExecutableTxsMapFrom = TrieMap.empty[Address, mutable.SortedMap[BigInteger, EthereumTransactionView]]
    // non executable address/nonces/inspect map
    val nonExecutableTxsMapInspect = TrieMap.empty[Address, mutable.SortedMap[BigInteger, String]]

    // proposition 1 non executable transactions
    val nonExecutableNonceTxsMap1: mutable.TreeMap[BigInteger, EthereumTransactionView] =
      new mutable.TreeMap[BigInteger, EthereumTransactionView]()
    // proposition 1 non executable transactions inspect
    val nonExecutableNonceTxsMapInspect1: mutable.TreeMap[BigInteger, String] =
      new mutable.TreeMap[BigInteger, String]()

    val nonExecutableTx1 = new EthereumTransaction(
      1997L,
      Optional.of(toProposition),
      BigInteger.valueOf(40L),
      BigInteger.valueOf(15467876L),
      BigInteger.valueOf(454545L),
      FeeUtils.INITIAL_BASE_FEE.add(BigInteger.valueOf(100)),
      BigInteger.valueOf(63000000L),
      BytesUtils.fromHexString("4aa64a075647e3621bbc14b03e4087903f2c9503"),
      defaultSignature
    )

    val nonExecutableTxView1 = new EthereumTransactionView(nonExecutableTx1)

    nonExecutableNonceTxsMap1.put(BigInteger.valueOf(40), nonExecutableTxView1)
    nonExecutableTxsMap.put(proposition1.address(), nonExecutableNonceTxsMap1)
    nonExecutableTxsMapFrom.put(proposition1.address(), nonExecutableNonceTxsMap1)

    nonExecutableNonceTxsMapInspect1.put(BigInteger.valueOf(40), "0x15532e34426cd5c37371ff455a5ba07501c0f522: 63000000 wei + 15467876 gas × 1000000100 wei")
    nonExecutableTxsMapInspect.put(proposition1.address(), nonExecutableNonceTxsMapInspect1)

    // mock getNonExecutableTransactionsMap method call
    Mockito.when(memoryPool.getNonExecutableTransactionsMap).thenReturn(nonExecutableTxsMap)
    // mock getNonExecutableTransactionsMapFrom method call
    Mockito.when(memoryPool.getNonExecutableTransactionsMapFrom(any[Address])).thenReturn(nonExecutableNonceTxsMap1)
    // mock getNonExecutableTransactionsMapInspect method call
    Mockito.when(memoryPool.getNonExecutableTransactionsMapInspect).thenReturn(nonExecutableTxsMapInspect)

    // return the mocked memory pool
    memoryPool
  }

  def getMockedAccountHistory(
      block: Option[AccountBlock],
      parentBlock: Option[AccountBlock] = None,
      genesisBlockId: Option[String] = None
  ): AccountHistory = {
    val history: AccountHistory = mock[AccountHistory]
    val blockId = block.get.id
    val height = if (genesis) 2 else 1
    val mcHeaderInfo = Some(MainchainHeaderInfo(
      generateMainchainHeaderHash(height),
      generateMainchainHeaderHash(height - 1),
      height,
      bytesToId(GENESIS_BLOCK_PARENT_ID),
      FieldElementUtils.randomFieldElementBytes(height)
    ))

    Mockito.when(history.params).thenReturn(mock[NetworkParams])
    val regTestParams = RegTestParams()
    Mockito.when(history.params.consensusSecondsInSlot).thenReturn(regTestParams.consensusSecondsInSlot)
    Mockito.when(history.params.consensusSlotsInEpoch).thenReturn(regTestParams.consensusSlotsInEpoch)

    Mockito.when(history.blockIdByHeight(any())).thenReturn(None)
    Mockito.when(history.blockIdByHeight(2)).thenReturn(Option(blockId))
    Mockito.when(history.bestBlock).thenReturn(block.orNull)

    if (genesis) {
      Mockito.when(history.params.sidechainGenesisBlockParentId).thenReturn(bytesToId(GENESIS_BLOCK_PARENT_ID))
      Mockito.when(history.blockIdByHeight(1)).thenReturn(genesisBlockId)
    }
    Mockito.when(history.getCurrentHeight).thenReturn(height)

    Mockito.when(history.getBlockById(any())).thenReturn(Optional.empty[AccountBlock])
    Mockito.when(history.getBlockById(blockId)).thenReturn(Optional.of(block.get))
    Mockito.when(history.modifierById(blockId)).thenReturn(block)

    Mockito.when(history.getStorageBlockById(any())).thenReturn(None)
    Mockito.when(history.getStorageBlockById(blockId)).thenReturn(Some(block.get))

    Mockito.when(history.getBestMainchainHeaderInfo).thenReturn(mcHeaderInfo)
    Mockito.when(history.missedMainchainReferenceDataHeaderHashes).thenReturn(Seq())

    Mockito.when(history.bestBlockInfo).thenReturn(mock[SidechainBlockInfo])
    Mockito.when(history.bestBlockInfo.withdrawalEpochInfo).thenReturn(mock[WithdrawalEpochInfo])
    Mockito.when(history.bestBlockInfo.withdrawalEpochInfo.lastEpochIndex).thenReturn(1)
    Mockito.when(history.bestBlockId).thenReturn(bytesToId(GENESIS_BLOCK_PARENT_ID))

    Mockito.when(history.feePaymentsInfo(any())).thenReturn(None)

    if (parentBlock.nonEmpty) {
      val parentId = parentBlock.get.id
      val blockInfo = new SidechainBlockInfo(
        height,
        0,
        parentId,
        86400L * 2,
        ModifierSemanticValidity.Unknown,
        MainchainHeaderBaseInfo
          .getMainchainHeaderBaseInfoSeqFromBlock(block.get, FieldElementFixture.generateFieldElement()),
        SidechainBlockInfo.mainchainReferenceDataHeaderHashesFromBlock(block.get),
        WithdrawalEpochInfo(0, 0),
        Option(VrfGenerator.generateVrfOutput(0)),
        parentId
      )
      Mockito.when(history.getBlockById(parentId)).thenReturn(Optional.of(parentBlock.get))
      Mockito.when(history.getStorageBlockById(parentId)).thenReturn(Some(parentBlock.get))
      Mockito.when(history.blockInfoById(any())).thenReturn(blockInfo)
    }
    Mockito.when(history.getBlockHeightById(any())).thenReturn(Optional.of[Integer](1))
    Mockito.when(history.getBlockHeightById(ArgumentMatchers.isNull[String])).thenReturn(Optional.empty[Integer]())
    Mockito.when(history.getBlockHeightById(ArgumentMatchers.matches("^xxx"))).thenReturn(Optional.empty[Integer]())
    history
  }

  def getMockedBlock(
      baseFee: BigInteger = FeeUtils.INITIAL_BASE_FEE,
      gasUsed: Long = 0L,
      gasLimit: BigInteger = DefaultGasFeeFork.blockGasLimit,
      blockId: ModifierId = null,
      parentBlockId: ModifierId = null,
      txs: Seq[SidechainTypes#SCAT] = Seq.empty[SidechainTypes#SCAT]
  ): AccountBlock = {
    val block: AccountBlock = mock[AccountBlock]

    val scCr1: SidechainCreation = mock[SidechainCreation]
    val ft1: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), MainNetParams().sidechainId, 1)
    val ft2: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), MainNetParams().sidechainId, 2)

    val mc2scTransactionsOutputs: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] =
      Seq(scCr1, ft1, ft2)
    val aggTx = new MC2SCAggregatedTransaction(
      mc2scTransactionsOutputs.asJava,
      MC2SCAggregatedTransaction.MC2SC_AGGREGATED_TRANSACTION_VERSION
    )

    var mcBlockRef: MainchainBlockReference = generateMainchainBlockReference()
    mcBlockRef = new MainchainBlockReference(
      mcBlockRef.header,
      MainchainBlockReferenceData(mcBlockRef.header.hash, Some(aggTx), None, None, Seq(), None)
    ) {
      override def semanticValidity(params: NetworkParams): Try[Unit] = Success(Unit)
    }

    val forgerAddress: AddressProposition = new AddressProposition(
      BytesUtils.fromHexString("1234567891011121314112345678910111213141")
    )
    Mockito.when(block.header).thenReturn(mock[AccountBlockHeader])
    Mockito.when(block.header.parentId).thenReturn(parentBlockId)
    Mockito.when(block.id).thenReturn(blockId)
    Mockito.when(block.header.baseFee).thenReturn(baseFee)
    Mockito.when(block.header.gasUsed).thenReturn(gasUsed)
    Mockito.when(block.header.gasLimit).thenReturn(gasLimit)
    Mockito
      .when(block.header.forgerAddress)
      .thenReturn(forgerAddress)
    Mockito.when(block.forgerPublicKey).thenReturn(new AddressProposition(BytesUtils.fromHexString("1111111111213141010203040506070809111222")))
    Mockito.when(block.sidechainTransactions).thenReturn(txs)
    Mockito.when(block.transactions).thenReturn(txs)
    Mockito.when(block.mainchainHeaders).thenReturn(Seq(mcBlockRef.header))
    Mockito.when(block.mainchainBlockReferencesData).thenReturn(Seq(mcBlockRef.data))
    Mockito.when(block.header.logsBloom).thenReturn(mock[Bloom])
    Mockito.when(block.header.logsBloom.getBytes).thenReturn(new Array[Byte](256))
    Mockito
      .when(block.header.sidechainTransactionsMerkleRootHash)
      .thenReturn(BytesUtils.fromHexString("1234567891011121314112345678910111213141010203040506070809111222"))
    Mockito
      .when(block.header.stateRoot)
      .thenReturn(BytesUtils.fromHexString("1234567891011121314112345678910111213141010203040506070809111333"))
    Mockito
      .when(block.header.receiptsRoot)
      .thenReturn(BytesUtils.fromHexString("1234567891011121314112345678910111213141010203040506070809111444"))
    Mockito.when(block.header.bytes).thenReturn(new Array[Byte](256))
    Mockito.when(block.bytes).thenReturn(new Array[Byte](256))
    Mockito.when(block.header.vrfOutput).thenReturn(VrfGenerator.generateVrfOutput(1111))
    Mockito.when(block.timestamp).thenReturn(1000000000L)
    Mockito.when(block.header.timestamp).thenReturn(1000000000L)
    block
  }

  def getMockedState(receipt: EthereumReceipt, txHash: Array[Byte]): AccountState = {
    val state: AccountState = mock[AccountState]
    val stateDB: StateDB = mock[StateDB]
    val metadataStorageView: AccountStateMetadataStorageView = {
      mock[AccountStateMetadataStorageView]
    }

    val mockMsgProcessor: MessageProcessor = setupMockMessageProcessor
    val msgProcessors = Seq(mockMsgProcessor)
    val stateView = new AccountStateView(metadataStorageView, stateDB, msgProcessors) {
      override lazy val withdrawalReqProvider: WithdrawalRequestProvider =
        msgProcessors.find(_.isInstanceOf[WithdrawalRequestProvider]).get.asInstanceOf[WithdrawalRequestProvider]
      override lazy val forgerStakesProvider: ForgerStakesProvider =
        msgProcessors.find(_.isInstanceOf[ForgerStakesProvider]).get.asInstanceOf[ForgerStakesProvider]
      override lazy val scTxCommTreeRootProvider: Option[ScTxCommitmentTreeRootHashMessageProvider] =
        Some(ScTxCommitmentTreeRootHashMessageProcessor)

      override def getProof(address: Address, keys: Array[Array[Byte]]): ProofAccountResult = {
        new ProofAccountResult(
          address,
          Array("123"),
          BigInteger.valueOf(123L),
          null,
          BigInteger.ONE,
          null,
          null
        )
      }

      override def getIntermediateRoot: Array[Byte] = new Array[Byte](MerkleTree.ROOT_HASH_LENGTH)
    }
    Mockito.when(state.params).thenReturn(RegTestParams())
    Mockito.when(state.getView).thenReturn(stateView)
    Mockito.when(state.getView.getTransactionReceipt(any())).thenReturn(None)
    Mockito.when(state.getView.getTransactionReceipt(txHash)).thenReturn(Some(receipt))
    if (state.getView != null) {
      Mockito.when(state.getView.getBalance(any())).thenReturn(BigInteger.valueOf(99999999999999999L))
      Mockito
        .when(state.getView.getBalance(new Address("0x1234567891011121314151617181920212223242")))
        .thenReturn(BigInteger.valueOf(123L))
      Mockito.when(state.getView.getCode(any())).thenReturn(Numeric.hexStringToByteArray("0x"))
      Mockito
        .when(state.getView.getCode(new Address("0x1234567891011121314151617181920212223242")))
        .thenReturn(Numeric.hexStringToByteArray("0x1234"))
      Mockito.when(state.getView.getNonce(any())).thenReturn(BigInteger.ZERO)
      Mockito
        .when(state.getView.getNonce(new Address("0x1234567891011121314151617181920212223242")))
        .thenReturn(BigInteger.ONE)
      Mockito.when(state.getView.getRefund).thenReturn(BigInteger.ONE)

      if (stateDB != null) {
        Mockito
          .when(stateDB.getStorage(any(), any()))
          .thenReturn(Hash.ZERO)
        Mockito
          .when(
            stateDB.getStorage(
              new Address("0x1234567890123456789012345678901234567890"),
              Hash.ZERO
            )
          )
          .thenReturn(
            new Hash("0x1411111111111111111111111111111111111111111111111111111111111111")
          )
        Mockito
          .when(
            stateDB.getStorage(
              new Address("0x1234567891011121314151617181920212223242"),
              Hash.ZERO
            )
          )
          .thenReturn(
            new Hash("0x1511111111111111111111111111111111111111111111111111111111111111")
          )
      }
    }
    Mockito.when(state.getBalance(any())).thenReturn(BigInteger.valueOf(999999999999999999L))
    Mockito
      .when(state.getBalance(new Address("0x1234567891011121314151617181920212223242")))
      .thenReturn(BigInteger.ZERO)
    Mockito.when(state.getStateDbViewFromRoot(any())).thenReturn(stateView)

    state
  }

  private def setupMockMessageProcessor = {
    val mockMsgProcessor = mock[MessageProcessor]
    Mockito
      .when(mockMsgProcessor.canProcess(any[Message], any[BaseAccountStateView]))
      .thenReturn(true)
    Mockito
      .when(mockMsgProcessor.process(any[Message], any[BaseAccountStateView], any[GasPool], any[BlockContext]))
      .thenReturn(Array.empty[Byte])
    mockMsgProcessor
  }

  def getMockedWallet(secret: PrivateKeySecp256k1): AccountWallet = {
    val wallet = new AccountWallet("seed".getBytes(StandardCharsets.UTF_8), getMockedSecretStorage(secret))

    wallet
  }

  def getMockedSecretStorage(secret: PrivateKeySecp256k1): SidechainSecretStorage = {
    val mockedSecretStorage: Storage = mock[Storage]
    val secretList = new ListBuffer[Secret]()
    val customSecretList = new ListBuffer[Secret]()
    val storedSecretList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    val secretVersions = new ListBuffer[ByteArrayWrapper]()
    val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
    customSecretSerializers.put(
      CustomPrivateKey.SECRET_TYPE_ID,
      CustomPrivateKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]]
    )
    val sidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)

    // Set base Secrets data
    secretList ++= getPrivateKey25519List(5).asScala
    customSecretList ++= getCustomPrivateKeyList(5).asScala

    secretVersions += getVersion
    storedSecretList.append({
      val key = new ByteArrayWrapper(secret.publicImage().address().toBytes)
      val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(secret))
      new Pair(key, value)
    })

    // Mock get and update methods of SecretStorage
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storedSecretList.asJava)

    new SidechainSecretStorage(mockedSecretStorage, sidechainSecretsCompanion)
  }
}
