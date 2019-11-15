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
import com.horizen.proposition.{MCPublicKeyHashProposition, Proposition, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.{RegularTransaction, TransactionSerializer}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import javafx.util.Pair
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import scorex.crypto.hash.Blake2b256
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

  val secret1 = PrivateKey25519Creator.getInstance().generateSecret("testSeed1".getBytes())
  val secret2 = PrivateKey25519Creator.getInstance().generateSecret("testSeed2".getBytes())
  val secret3 = PrivateKey25519Creator.getInstance().generateSecret("testSeed3".getBytes())
  val box_1 = new RegularBox(secret1.publicImage(), 1, 10)
  val box_2 = new RegularBox(secret2.publicImage(), 1, 20)
  val box_3 = new RegularBox(secret3.publicImage(), 1, 30)
  val allBoxes: util.List[Box[Proposition]] = walletAllBoxes()
  val transactionList: util.List[RegularTransaction] = getTransactionList()

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
    val list: util.List[Box[Proposition]] = new util.ArrayList[Box[Proposition]]()
    list.add(box_1.asInstanceOf[Box[Proposition]])
    list.add(box_2.asInstanceOf[Box[Proposition]])
    list.add(box_3.asInstanceOf[Box[Proposition]])
    list
  }

  def getNodeWalletMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeWallet = {
    val wallet: NodeWallet = mock[NodeWallet]
    Mockito.when(wallet.boxesBalance(ArgumentMatchers.any())).thenAnswer(_ => Long.box(1000))
    Mockito.when(wallet.allBoxesBalance).thenAnswer(_ => Long.box(5500))

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

    val listOfSecrets = List(secret1, secret2)

    Mockito.when(wallet.secretsOfType(ArgumentMatchers.any())).thenAnswer(_ => listOfSecrets.asJava)

    Mockito.when(wallet.walletSeed()).thenAnswer(_ => "a seed".getBytes)

    Mockito.when(wallet.allSecrets()).thenAnswer(_ => listOfSecrets.asJava)

    Mockito.when(wallet.secretByPublicKey(ArgumentMatchers.any[Proposition])).thenAnswer(asw => {
      val prop: Proposition = asw.getArgument(0).asInstanceOf[Proposition]
      if(BytesUtils.toHexString(prop.bytes).equals(BytesUtils.toHexString(secret1.publicImage().bytes))) Optional.of(secret1)
      else if(BytesUtils.toHexString(prop.bytes).equals(BytesUtils.toHexString(secret2.publicImage().bytes))) Optional.of(secret2)
      else if(BytesUtils.toHexString(prop.bytes).equals(BytesUtils.toHexString(secret3.publicImage().bytes))) Optional.of(secret3)
      else Optional.empty()
    })

    wallet
  }

  private def getTransaction(fee: Long): RegularTransaction = {
    val from: util.List[Pair[RegularBox, PrivateKey25519]] = new util.ArrayList[Pair[RegularBox, PrivateKey25519]]()
    val to: util.List[Pair[PublicKey25519Proposition, java.lang.Long]] = new util.ArrayList[Pair[PublicKey25519Proposition, java.lang.Long]]()
    val withdrawalRequests: util.List[Pair[MCPublicKeyHashProposition, java.lang.Long]] = new util.ArrayList[Pair[MCPublicKeyHashProposition, java.lang.Long]]()

    from.add(new Pair(box_1, secret1))
    from.add(new Pair(box_2, secret2))

    to.add(new Pair(secret3.publicImage(), 10L))

    RegularTransaction.create(from, to, withdrawalRequests, fee, 1547798549470L)
  }

  private def getTransactionList(): util.List[RegularTransaction] = {
    val list: util.List[RegularTransaction] = new util.ArrayList[RegularTransaction]()
    list.add(getTransaction(3L))
    list.add(getTransaction(7L))
    list
  }

  def getNodeMemoryPoolMock(sidechainApiMockConfiguration: SidechainApiMockConfiguration): NodeMemoryPool = {
    val memoryPool: NodeMemoryPool = mock[NodeMemoryPool]

    Mockito.when(memoryPool.getTransactions).thenAnswer(_ => transactionList)

    memoryPool
  }

  def getSidechainNodeView(sidechainApiMockConfiguration: SidechainApiMockConfiguration): SidechainNodeView =
    new SidechainNodeView(
      getNodeHistoryMock(sidechainApiMockConfiguration),
      getNodeStateMock(sidechainApiMockConfiguration),
      getNodeWalletMock(sidechainApiMockConfiguration),
      getNodeMemoryPoolMock(sidechainApiMockConfiguration))

}