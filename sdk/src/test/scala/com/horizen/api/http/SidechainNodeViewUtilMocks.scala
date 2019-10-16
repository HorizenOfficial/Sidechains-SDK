package com.horizen.api.http

import java.time.Instant
import java.util.Optional
import java.{lang, util}

import com.horizen.SidechainTypes
import com.horizen.block.{MainchainBlockReference, SidechainBlock}
import com.horizen.box.{Box, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}
import com.horizen.params.MainNetParams
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.{RegularTransaction, TransactionSerializer}
import com.horizen.utils.BytesUtils
import javafx.util.Pair
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import scorex.crypto.signatures.Curve25519
import scorex.util.bytesToId

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Success}

class SidechainNodeViewUtilMocks extends MockitoSugar {

  val mainchainBlockReferenceInfoRef = new MainchainBlockReferenceInfo(
    BytesUtils.fromHexString("0000000011aec26c29306d608645a644a592e44add2988a9d156721423e714e0"),
    BytesUtils.fromHexString("00000000106843ee0119c6db92e38e8655452fd85f638f6640475e8c6a3a3582"),
    230, BytesUtils.fromHexString("69c4f36c2b3f546aa57fa03c4df51923e17e8ea59ecfdea7f49c8aff06ec8208"))

  def getNodeHistoryMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeHistory = {
    val history: NodeHistory = mock[NodeHistory]

    val genesisBlock: SidechainBlock = SidechainBlock.create(bytesToId(new Array[Byte](32)), Instant.now.getEpochSecond - 10000, Seq(), Seq(),
      PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(6543211L).getBytes),
      SidechainTransactionsCompanion(new util.HashMap[lang.Byte, TransactionSerializer[SidechainTypes#SCBT]]()), null).get

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

    Mockito.when(history.getBlockIdByHeight(ArgumentMatchers.any())).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getBlockIdByHeight_return_value()) Optional.of("the_block_id")
      else Optional.empty())

    Mockito.when(history.getCurrentHeight).thenAnswer(_ =>
      if (sidechainApiMockConfiguration.getShould_history_getCurrentHeight_return_value()) 230
      else 0)

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
        val mcBlockHex = Source.fromResource("mcblock473173").getLines().next()
        val mcBlockBytes = BytesUtils.fromHexString(mcBlockHex)
        MainchainBlockReference.create(mcBlockBytes, new MainNetParams()) match {
          case Success(ref) => Optional.of(ref)
          case Failure(exception) => Optional.empty()
        }
      }
      else Optional.empty())

    history
  }

  def getNodeStateMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeState = {
    mock[NodeState]
  }

  private def walletAllBoxes(): util.List[Box[Proposition]] = {
    val box_1: Box[Proposition] = mock[Box[Proposition]]
    val box_2: Box[Proposition] = mock[Box[Proposition]]
    val box_3: Box[Proposition] = mock[Box[Proposition]]

    Mockito.when(box_1.proposition()).thenAnswer(_ => new PublicKey25519Proposition(Curve25519.createKeyPair("12345".getBytes)._2))
    Mockito.when(box_2.proposition()).thenAnswer(_ => new PublicKey25519Proposition(Curve25519.createKeyPair("12345".getBytes)._2))
    Mockito.when(box_3.proposition()).thenAnswer(_ => new PublicKey25519Proposition(Curve25519.createKeyPair("12345".getBytes)._2))

    Mockito.when(box_1.value()).thenAnswer(_ => Long.box(10))
    Mockito.when(box_2.value()).thenAnswer(_ => Long.box(20))
    Mockito.when(box_3.value()).thenAnswer(_ => Long.box(30))

    Mockito.when(box_1.id()).thenAnswer(_ => "box_1_id".getBytes)
    Mockito.when(box_2.id()).thenAnswer(_ => "box_2_id".getBytes)
    Mockito.when(box_3.id()).thenAnswer(_ => "box_3_id".getBytes)

    val list: util.List[Box[Proposition]] = new util.ArrayList[Box[Proposition]]()
    list.add(box_1)
    list.add(box_2)
    list.add(box_3)
    list
  }

  def getNodeWalletMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeWallet = {
    val wallet: NodeWallet = mock[NodeWallet]
    Mockito.when(wallet.boxesBalance(ArgumentMatchers.any())).thenAnswer(_ => Long.box(1000))
    Mockito.when(wallet.allBoxesBalance).thenAnswer(_ => Long.box(5500))

    val allBoxes = walletAllBoxes()

    Mockito.when(wallet.allBoxes()).thenAnswer(_ => allBoxes)
    Mockito.when(wallet.allBoxes(ArgumentMatchers.any[util.List[Array[Byte]]])).thenAnswer(asw => {
      val args = asw.getArguments
      if (args != null && args.length > 0) {
        val arg = asw.getArgument(0).asInstanceOf[util.List[Array[Byte]]]
        if (arg.size() > 0)
          allBoxes.asScala.toList.filter(box => !BytesUtils.contains(arg, box.id())).asJava
        else allBoxes
      }
      else
        allBoxes
    })

    val secret1 = PrivateKey25519Creator.getInstance().generateSecret("testSeed1".getBytes())
    val secret2 = PrivateKey25519Creator.getInstance().generateSecret("testSeed2".getBytes())
    val listOfSecrets = List(secret1, secret2)

    Mockito.when(wallet.secretsOfType(ArgumentMatchers.any())).thenAnswer(_ => listOfSecrets.asJava)

    Mockito.when(wallet.walletSeed()).thenAnswer(_ => "a seed".getBytes)

    Mockito.when(wallet.allSecrets()).thenAnswer(_ => listOfSecrets.asJava)

    wallet
  }

  private def getTransaction(seed1: String, seed2: String, seed3: String): RegularTransaction = {
    val pkc = PrivateKey25519Creator.getInstance()
    val pk1 = pkc.generateSecret(seed1.getBytes())
    val pk2 = pkc.generateSecret(seed2.getBytes())
    val pk3 = pkc.generateSecret(seed3.getBytes())
    val from: util.List[Pair[RegularBox, PrivateKey25519]] = new util.ArrayList[Pair[RegularBox, PrivateKey25519]]()
    val to: util.List[Pair[PublicKey25519Proposition, java.lang.Long]] = new util.ArrayList[Pair[PublicKey25519Proposition, java.lang.Long]]()

    from.add(new Pair(new RegularBox(pk1.publicImage(), 1, 10), pk1))
    from.add(new Pair(new RegularBox(pk2.publicImage(), 1, 20), pk2))

    to.add(new Pair(pk3.publicImage(), 10L))

    RegularTransaction.create(from, to, 10L, 1547798549470L)
  }

  def getTransactionList(): util.List[RegularTransaction] = {
    val list: util.List[RegularTransaction] = new util.ArrayList[RegularTransaction]()
    list.add(getTransaction("seed1", "seed2", "seed3"))
    list.add(getTransaction("seed4", "seed5", "seed6"))
    list
  }

  def getNodeMemoryPoolMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeMemoryPool = {
    val memoryPool: NodeMemoryPool = mock[NodeMemoryPool]

    Mockito.when(memoryPool.getTransactions).thenAnswer(_ => getTransactionList())

    memoryPool
  }

  def getSidechainNodeView(sidechainApiMockConfiguration: SidechainApiMockConfiguration): SidechainNodeView =
    new SidechainNodeView(
      getNodeHistoryMock(sidechainApiMockConfiguration),
      getNodeStateMock(sidechainApiMockConfiguration),
      getNodeWalletMock(sidechainApiMockConfiguration),
      getNodeMemoryPoolMock(sidechainApiMockConfiguration))

}