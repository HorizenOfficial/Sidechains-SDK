package com.horizen.api.http

import java.time.Instant
import java.util
import java.util.{Optional, ArrayList => JArrayList, List => JList}
import com.horizen.block.MainchainBlockReference
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, KeyRotationProofTypes}
import com.horizen.chain.{MainchainHeaderBaseInfo, MainchainHeaderHash, SidechainBlockInfo, byteArrayToMainchainHeaderHash}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.fixtures.{BoxFixture, CompanionsFixture, FieldElementFixture, ForgerBoxFixture, MerkleTreeFixture, VrfGenerator}
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.params.MainNetParams
import com.horizen.proposition.{Proposition, PublicKey25519Proposition, PublicKey25519PropositionSerializer}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator, SchnorrKeyGenerator}
import com.horizen.utils.{BytesUtils, Pair, TestSidechainsVersionsManager}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.mockito.MockitoSugar
import com.horizen.utils.WithdrawalEpochInfo
import com.horizen.utxo.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import com.horizen.utxo.block.SidechainBlock
import com.horizen.utxo.box.{Box, ZenBox}
import com.horizen.utxo.box.data.{BoxData, ZenBoxData}
import com.horizen.utxo.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import com.horizen.utxo.state.ApplicationState
import com.horizen.utxo.transaction.RegularTransaction
import com.horizen.utxo.wallet.ApplicationWallet
import com.horizen.vrf.{VrfGeneratedDataProvider, VrfOutput}
import sparkz.core.consensus.ModifierSemanticValidity
import sparkz.util.{ModifierId, bytesToId, idToBytes}
import sparkz.core.NodeViewHolder.CurrentView

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Success, Try}

class SidechainNodeViewUtilMocks extends MockitoSugar with BoxFixture with CompanionsFixture {

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val mainchainBlockReferenceInfoRef = new MainchainBlockReferenceInfo(
    BytesUtils.fromHexString("0000000011aec26c29306d608645a644a592e44add2988a9d156721423e714e0"),
    BytesUtils.fromHexString("00000000106843ee0119c6db92e38e8655452fd85f638f6640475e8c6a3a3582"),
    230,
    BytesUtils.fromHexString("69c4f36c2b3f546aa57fa03c4df51923e17e8ea59ecfdea7f49c8aff06ec8208"),
    BytesUtils.fromHexString("69c4f36c2b3f546aa57fa03c4df51923e17e8ea59ecfdea7f49c8aff06ec8208")) // TO DO: check, probably use different sc id

  val secret1 = PrivateKey25519Creator.getInstance().generateSecret("testSeed1".getBytes(StandardCharsets.UTF_8))
  val secret2 = PrivateKey25519Creator.getInstance().generateSecret("testSeed2".getBytes(StandardCharsets.UTF_8))
  val secret3 = PrivateKey25519Creator.getInstance().generateSecret("testSeed3".getBytes(StandardCharsets.UTF_8))
  val secret4 = PrivateKey25519Creator.getInstance().generateSecret("testSeed4".getBytes(StandardCharsets.UTF_8))
  val box_1 = getZenBox(secret1.publicImage(), 1, 10)
  val box_2 = getZenBox(secret2.publicImage(), 1, 20)
  val box_3 = getZenBox(secret3.publicImage(), 1, 30)
  val box_4 = getForgerBox(secret4.publicImage(), 2, 30, secret4.publicImage(), getVRFPublicKey(4L))
  val box_5 = getCustomBox

  val allBoxes: util.List[Box[Proposition]] = walletAllBoxes()
  val transactionList: util.List[RegularTransaction] = getTransactionList

  val (forgingBox, forgerBoxMetadata) = ForgerBoxFixture.generateForgerBox(234)
  val genesisBlock: SidechainBlock = SidechainBlock.create(
    bytesToId(new Array[Byte](32)),
    SidechainBlock.BLOCK_VERSION,
    Instant.now.getEpochSecond - 10000,
    Seq(),
    Seq(),
    Seq(),
    Seq(),
    forgerBoxMetadata.blockSignSecret,
    forgerBoxMetadata.forgingStakeInfo,
    VrfGenerator.generateProof(456L),
    MerkleTreeFixture.generateRandomMerklePath(456L),
    new Array[Byte](32),
    sidechainTransactionsCompanion).get

  val mainchainHeadersBaseInfo: Seq[MainchainHeaderBaseInfo] = Seq(
    MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(BytesUtils.fromHexString("0269861FB647BA5730425C79AC164F8A0E4003CF30990628D52CEE50DFEC9213")),
      BytesUtils.fromHexString("d6847c3b30727116b109caa0dae303842cbd26f9041be6cf05072e9f3211a457")),
    MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(BytesUtils.fromHexString("E78283E4B2A92784F252327374D6D587D0A4067373AABB537485812671645B70")),
      BytesUtils.fromHexString("04c9d52cd10e1798ecce5752bf9dc1675ee827dd1568eee78fe4a5aa7ff1e6bd")),
    MainchainHeaderBaseInfo(byteArrayToMainchainHeaderHash(BytesUtils.fromHexString("77B57DC4C97CD30AABAA00722B0354BE59AB74397177EA1E2A537991B39C7508")),
      BytesUtils.fromHexString("8e1a02ff813f5023ab656e4ec55d8683fbb63f6c9e4339741de0696c6553a4ca"))
  )
  val mainchainReferenceDataHeaderHashes: Seq[MainchainHeaderHash] = Seq("CEE50DFEC92130269861FB647BA5730425C79AC164F8A0E4003CF30990628D52",
                                                                          "0269861FB647BA5730425C79AC164F8A0E4003CF30990628D52CEE50DFEC9213")
    .map(hex => byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(hex)))

  val vrfGenerationSeed = 234
  val vrfGenerationPrefix = "SidechainBlockInfoTest"
  val vrfOutput: VrfOutput = VrfGeneratedDataProvider.getVrfOutput(vrfGenerationSeed);

  val genesisBlockInfo = SidechainBlockInfo(
    1,
    1,
    genesisBlock.parentId,
    genesisBlock.timestamp,
    ModifierSemanticValidity.Valid,
    mainchainHeadersBaseInfo,
    mainchainReferenceDataHeaderHashes,
    WithdrawalEpochInfo(10, 100),
    Some[VrfOutput]( vrfOutput),
    genesisBlock.parentId
  )

  def getNodeHistoryMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeHistory = {
    val history: NodeHistory = mock[NodeHistory]

    Mockito.when(history.getBlockById(ArgumentMatchers.any[String])).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getBlockById_return_value()) Optional.of(genesisBlock)
      else Optional.empty())

    Mockito.when(history.getLastBlockIds(ArgumentMatchers.any())).thenAnswer(_ => {
      val ids = new util.ArrayList[String]()
      ids.add("block_id_1")
      ids.add("block_id_2")
      ids.add("block_id_3")
      ids
    })

    Mockito.when(history.getBestBlock).thenAnswer(_ => genesisBlock)

    Mockito.when(history.getBlockHeightById(ArgumentMatchers.any[String])).thenAnswer(_ =>Optional.of(100))

    Mockito.when(history.getBlockIdByHeight(ArgumentMatchers.any())).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getBlockIdByHeight_return_value()) Optional.of("the_block_id")
      else Optional.empty())

    Mockito.when(history.getCurrentHeight).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getCurrentHeight_return_value()) 230
      else 0)

    Mockito.when(history.getBlockInfoById(ArgumentMatchers.any[String])).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getBlockInfoById_return_value()) Optional.of(genesisBlockInfo)
      else Optional.empty())

    Mockito.when(history.isInActiveChain(ArgumentMatchers.any[String])).thenAnswer(_ => true)

    Mockito.when(history.getBestMainchainBlockReferenceInfo).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getBestMainchainBlockReferenceInfo_return_value())
        Optional.of(mainchainBlockReferenceInfoRef)
      else Optional.empty())

    Mockito.when(history.getMainchainBlockReferenceInfoByMainchainBlockHeight(ArgumentMatchers.any())).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getMainchainBlockReferenceInfoByMainchainBlockHeight_return_value())
        Optional.of(mainchainBlockReferenceInfoRef)
      else Optional.empty())

    Mockito.when(history.getMainchainBlockReferenceInfoByHash(ArgumentMatchers.any())).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getMainchainBlockReferenceInfoByHash_return_value())
        Optional.of(mainchainBlockReferenceInfoRef)
      else Optional.empty())

    Mockito.when(history.getMainchainBlockReferenceByHash(ArgumentMatchers.any())).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getMainchainBlockReferenceByHash_return_value()) {
        val mcBlockHex = Source.fromResource("mcblock473173_mainnet").getLines().next()
        val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
        MainchainBlockReference.create(mcBlockBytes, MainNetParams(), TestSidechainsVersionsManager()) match {
          case Success(ref) => Optional.of(ref)
          case Failure(exception) => Optional.empty()
        }
      }
      else Optional.empty())

    Mockito.when(history.searchTransactionInsideSidechainBlock(ArgumentMatchers.any[String], ArgumentMatchers.any[String])).thenAnswer(asw => {
      if (sidechainApiMockConfiguration.getShould_history_searchTransactionInBlock_return_value()) {
        val id = asw.getArgument(0).asInstanceOf[String]
        Optional.ofNullable(Try(transactionList.asScala.filter(tx => BytesUtils.toHexString(idToBytes(ModifierId @@ tx.id)).equalsIgnoreCase(id)).head).getOrElse(null))
      } else
        Optional.empty()
    })

    history
  }

  def getNodeStateMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeState = {
    val nodeState: NodeState = mock[NodeState]
    Mockito.when(nodeState.hasCeased()).thenReturn(true)
    nodeState
  }

  private def walletAllBoxes(): util.List[Box[Proposition]] = {
    val list: util.List[Box[Proposition]] = new util.ArrayList[Box[Proposition]]()
    list.add(box_1.asInstanceOf[Box[Proposition]])
    list.add(box_2.asInstanceOf[Box[Proposition]])
    list.add(box_3.asInstanceOf[Box[Proposition]])
    list.add(box_4.asInstanceOf[Box[Proposition]])
    list.add(box_5.asInstanceOf[Box[Proposition]])
    list
  }
  val listOfSecrets = List(secret1, secret2)
  val listOfPropositions = listOfSecrets.map(secret => secret.publicImage())

  def getNodeWalletMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeWallet = {
    val wallet: NodeWallet = mock[NodeWallet]
    Mockito.when(wallet.boxesBalance(ArgumentMatchers.any())).thenAnswer(_ => Long.box(1000))
    Mockito.when(wallet.allCoinsBoxesBalance()).thenAnswer(_ => Long.box(5500))

    Mockito.when(wallet.allBoxes()).thenAnswer(_ => allBoxes)
    Mockito.when(wallet.allBoxes(ArgumentMatchers.any[util.List[Array[Byte]]])).thenAnswer(asw => {
      val args = asw.getArguments
      if (args != null && args.nonEmpty) {
        val arg = asw.getArgument(0).asInstanceOf[util.List[Array[Byte]]]
        if (arg.size() > 0)
          allBoxes.asScala.toList.filter(box => !BytesUtils.contains(arg, box.id())).asJava
        else allBoxes
      }
      else
        allBoxes
    })


    Mockito.when(wallet.secretsOfType(ArgumentMatchers.any())).thenAnswer(_ => listOfSecrets.asJava)

    Mockito.when(wallet.walletSeed()).thenAnswer(_ => "a seed".getBytes(StandardCharsets.UTF_8))

    Mockito.when(wallet.allSecrets()).thenAnswer(_ => listOfSecrets.asJava)

    Mockito.when(wallet.secretByPublicKey25519Proposition(ArgumentMatchers.any[PublicKey25519Proposition])).thenAnswer(asw => {
      val prop: Proposition = asw.getArgument(0).asInstanceOf[Proposition]
      if(BytesUtils.toHexString(prop.bytes).equals(BytesUtils.toHexString(secret1.publicImage().bytes))) Optional.of(secret1)
      else if(BytesUtils.toHexString(prop.bytes).equals(BytesUtils.toHexString(secret2.publicImage().bytes))) Optional.of(secret2)
      else if(BytesUtils.toHexString(prop.bytes).equals(BytesUtils.toHexString(secret3.publicImage().bytes))) Optional.of(secret3)
      else if(BytesUtils.toHexString(prop.bytes).equals(BytesUtils.toHexString(secret4.publicImage().bytes))) Optional.of(secret4)
      else Optional.empty()
    })

    Mockito.when(wallet.boxesOfType(ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer(asw => {
      allBoxes
    })

    Mockito.when(wallet.secretByPublicKeyBytes(ArgumentMatchers.any[Array[Byte]])).thenAnswer(asw => {
      val prop = asw.getArgument(0).asInstanceOf[Array[Byte]]
      if(util.Arrays.equals(prop, secret1.publicImage().bytes)) Optional.of(secret1)
      else if(util.Arrays.equals(prop, secret2.publicImage().bytes)) Optional.of(secret2)
      else if(util.Arrays.equals(prop, secret3.publicImage().bytes)) Optional.of(secret3)
      else if(util.Arrays.equals(prop, secret4.publicImage().bytes)) Optional.of(secret4)
      else Optional.empty()
    })

    wallet
  }

  private def getTransaction(fee: Long): RegularTransaction = {
    val from: util.List[Pair[ZenBox, PrivateKey25519]] = new util.ArrayList[Pair[ZenBox, PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition, _ <: Box[_ <: Proposition]]] = new JArrayList()

    from.add(new Pair(box_1, secret1))
    from.add(new Pair(box_2, secret2))

    to.add(new ZenBoxData(secret3.publicImage(), box_1.value() + box_2.value() - fee))

    RegularTransaction.create(from, to, fee)
  }

  private def getTransactionList: util.List[RegularTransaction] = {
    val list: util.List[RegularTransaction] = new util.ArrayList[RegularTransaction]()
    list.add(getTransaction(1L))
    list.add(getTransaction(1L))
    list
  }

  def getNodeMemoryPoolMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeMemoryPool = {
    val memoryPool: NodeMemoryPool = mock[NodeMemoryPool]

    Mockito.when(memoryPool.getTransactions).thenAnswer(_ => transactionList)

    Mockito.when(memoryPool.getTransactionsSortedByFee(ArgumentMatchers.any())).thenAnswer(_ => {
      if (sidechainApiMockConfiguration.getShould_history_getTransactionsSortedByFee_return_value())
        transactionList.asScala.sortBy(_.fee()).asJava
      else null
    })

    Mockito.when(memoryPool.getTransactionById(ArgumentMatchers.any[String])).thenAnswer(asw => {
      if (sidechainApiMockConfiguration.getShould_memPool_searchTransactionInMemoryPool_return_value()) {
        val id = asw.getArgument(0).asInstanceOf[String]
        Optional.ofNullable(Try(transactionList.asScala.filter(tx => BytesUtils.toHexString(idToBytes(ModifierId @@ tx.id)).equalsIgnoreCase(id)).head).getOrElse(null))
      } else
        Optional.empty()
    })

    memoryPool
  }

  def getSidechainNodeView(sidechainApiMockConfiguration: SidechainApiMockConfiguration): SidechainNodeView =
      new SidechainNodeView(
        getNodeHistoryMock(sidechainApiMockConfiguration),
        getNodeStateMock(sidechainApiMockConfiguration),
        getNodeWalletMock(sidechainApiMockConfiguration),
        getNodeMemoryPoolMock(sidechainApiMockConfiguration),
        mock[ApplicationState],
        mock[ApplicationWallet])

  val schnorrSecret = SchnorrKeyGenerator.getInstance().generateSecret("seed".getBytes(StandardCharsets.UTF_8))
  val schnorrSecret2 = SchnorrKeyGenerator.getInstance().generateSecret("seed2".getBytes(StandardCharsets.UTF_8))
  val schnorrSecret3 = SchnorrKeyGenerator.getInstance().generateSecret("seed3".getBytes(StandardCharsets.UTF_8))
  val schnorrSecret4 = SchnorrKeyGenerator.getInstance().generateSecret("seed4".getBytes(StandardCharsets.UTF_8))

  val schnorrProof = schnorrSecret.sign(FieldElementFixture.generateFieldElement())
  val keyRotationProof = KeyRotationProof(KeyRotationProofTypes(0), 0, schnorrSecret.publicImage(), schnorrProof, schnorrProof)
  val signerKeys = Seq(schnorrSecret.publicImage(), schnorrSecret2.publicImage())
  val masterKeys = Seq(schnorrSecret3.publicImage(), schnorrSecret4.publicImage())
  val certifiersKeys = CertifiersKeys(signerKeys.toVector, masterKeys.toVector)

  type NodeView = CurrentView[Any, Any, Any, Any]

  def getSidechainNodeHistoryMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): SidechainHistory = {
    val history: SidechainHistory = mock[SidechainHistory]
    history
  }

  def getSidechainNodeStateMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): SidechainState = {
    val nodeState: SidechainState = mock[SidechainState]

    Mockito.when(nodeState.keyRotationProof(ArgumentMatchers.any[Int], ArgumentMatchers.any[Int], ArgumentMatchers.any[Int])).thenReturn(Some(keyRotationProof))

    Mockito.when(nodeState.getWithdrawalEpochInfo).thenReturn(WithdrawalEpochInfo(0,0))

    Mockito.when(nodeState.certifiersKeys(ArgumentMatchers.any[Int])).thenReturn(Some(certifiersKeys))

    nodeState
  }

  def getSidechainNodeWalletMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): SidechainWallet = {
    val wallet: SidechainWallet = mock[SidechainWallet]
    wallet
  }

  def getSidechainNodeMemoryPoolMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): SidechainMemoryPool = {
    val memoryPool: SidechainMemoryPool = mock[SidechainMemoryPool]
    memoryPool
  }

  def getNodeView(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeView = {
    new NodeView(
      getSidechainNodeHistoryMock(sidechainApiMockConfiguration),
      getSidechainNodeStateMock(sidechainApiMockConfiguration),
      getSidechainNodeWalletMock(sidechainApiMockConfiguration),
      getSidechainNodeMemoryPoolMock(sidechainApiMockConfiguration),
    )
  }

  val listOfNodeStorageVersion : Map[String, String] =  Map(("history", "jdfhsjghf"), ("wallet", ""), ("state", "dsdfg4353nfsgsg"))
  val sidechainId = "1f56e0a44b48148ed70a69ad3529d646a8ca6c537f941f80ea9cf445460c7809"
  val sidechainIdArray = BytesUtils.reverseBytes(BytesUtils.fromHexString(sidechainId))

}
