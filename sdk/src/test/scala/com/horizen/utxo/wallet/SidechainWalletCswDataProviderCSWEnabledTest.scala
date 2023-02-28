package com.horizen.utxo.wallet

import com.horizen.SidechainTypes
import com.horizen.block.{MainchainBlockReferenceData, SidechainCommitmentTree}
import com.horizen.fixtures._
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.proposition._
import com.horizen.storage._
import com.horizen.transaction.MC2SCAggregatedTransaction
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation, SidechainRelatedMainchainOutput}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import com.horizen.utxo.block.SidechainBlock
import com.horizen.utxo.box.{Box, CoinsBox}
import com.horizen.utxo.state.UtxoMerkleTreeView
import com.horizen.utxo.storage.SidechainWalletCswDataStorage
import com.horizen.utxo.utils.{CswData, ForwardTransferCswData, UtxoCswData}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import scala.collection.JavaConverters._
import scala.util.{Failure, Random, Success}

class SidechainWalletCswDataProviderCSWEnabledTest
  extends JUnitSuite
    with SidechainRelatedMainchainOutputFixture
    with TransactionFixture
    with CompanionsFixture
    with StoreFixture
    with MockitoSugar
{
  val mockedCswDataStorage: Storage = mock[Storage]

  val withdrawalEpochNumber: Int = 1

  val params: NetworkParams = MainNetParams()

  def boxIdToMerklePath(boxId: Array[Byte]): Array[Byte] = BytesUtils.reverseBytes(boxId)

  @Test
  def testCalculateUtxoCswData(): Unit = {
    val mockedCswDataStorage: SidechainWalletCswDataStorage = mock[SidechainWalletCswDataStorage]
    val cswDataProviderWithCSW = SidechainWalletCswDataProviderCSWEnabled(mockedCswDataStorage)

    val boxes: Seq[SidechainTypes#SCB] = Seq(
      getCustomBox.asInstanceOf[SidechainTypes#SCB], // non-coin box
      getZenBox,    // coin box
      getForgerBox, // coin box
      getCustomBox.asInstanceOf[SidechainTypes#SCB] // non-coin box
    )

    val walletBoxes = boxes.map(box => new WalletBox(box, createdAt = 123456789L))

    val utxoMerkleTreeView: UtxoMerkleTreeView = mock[UtxoMerkleTreeView]
    Mockito.when(utxoMerkleTreeView.utxoMerklePath(ArgumentMatchers.any[Array[Byte]]())).thenAnswer(args => {
      val boxId: Array[Byte] = args.getArgument(0)
      Some(boxIdToMerklePath(boxId))
    })

    val expectedCswData = boxes.withFilter(_.isInstanceOf[CoinsBox[_]]).map(b => walletBoxToCswData(b, utxoMerkleTreeView))
    val cswData = cswDataProviderWithCSW.calculateUtxoCswData(utxoMerkleTreeView,walletBoxes.toList)

    assertEquals("Different CSW data found.", expectedCswData, cswData)
  }

  @Test
  def testCalculateForwardTransferCswData(): Unit = {
    val mockedCswDataStorage: SidechainWalletCswDataStorage = mock[SidechainWalletCswDataStorage]

    val cswDataProviderWithCSW = SidechainWalletCswDataProviderCSWEnabled(mockedCswDataStorage)

    val pubKeys: Set[SidechainTypes#SCP] = Set(
      getPrivateKey25519.publicImage(),
      getPrivateKey25519.publicImage(),
      getPrivateKey25519.publicImage()
    )


    // Test 1: RefData without MC2SCAggTx
    val emptyRefData: MainchainBlockReferenceData = MainchainBlockReferenceData(null, sidechainRelatedAggregatedTransaction = None, None, None, Seq(), None)
    assertTrue("No CSW data expected to be found.", cswDataProviderWithCSW.calculateForwardTransferCswData(Seq(emptyRefData), pubKeys, params).isEmpty)


    // Test 2: RefData with MC2SCAggTx, but with related ScCr, but without related FTs
    val scCr1: SidechainCreation = mock[SidechainCreation]
    Mockito.when(scCr1.getBox).thenReturn(getForgerBox(pubKeys.head.asInstanceOf[PublicKey25519Proposition]))
    val ft1: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), params.sidechainId)
    val ft2: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), params.sidechainId)

    var mc2scTransactionsOutputs: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = Seq(scCr1, ft1, ft2)
    var aggTx = new MC2SCAggregatedTransaction(mc2scTransactionsOutputs.asJava, MC2SCAggregatedTransaction.MC2SC_AGGREGATED_TRANSACTION_VERSION)

    val refData: MainchainBlockReferenceData = MainchainBlockReferenceData(null, Some(aggTx), None, None, Seq(), None)
    assertTrue("No CSW data expected to be found.", cswDataProviderWithCSW.calculateForwardTransferCswData(Seq(refData), pubKeys, params).isEmpty)


    // Test 2: RefData with MC2SCAggTx with wallet related FTs
    // Define FT outputs related to us
    val walletFt1: ForwardTransfer = getForwardTransfer(pubKeys.head.asInstanceOf[PublicKey25519Proposition], params.sidechainId)
    val walletFt2: ForwardTransfer = getForwardTransfer(pubKeys.last.asInstanceOf[PublicKey25519Proposition], params.sidechainId)

    mc2scTransactionsOutputs = Seq(walletFt1, ft1, ft2, walletFt2)
    aggTx = new MC2SCAggregatedTransaction(mc2scTransactionsOutputs.asJava, MC2SCAggregatedTransaction.MC2SC_AGGREGATED_TRANSACTION_VERSION)

    val refDataWithFTs: MainchainBlockReferenceData = MainchainBlockReferenceData(null, Some(aggTx), None, None, Seq(), None)

    val commTree = refDataWithFTs.commitmentTree(params.sidechainId, params.sidechainCreationVersion)
    val expectedCswData = Seq(
      ftToCswData(walletFt1, 0, commTree),
      ftToCswData(walletFt2, 3, commTree)
    )
    commTree.free()

    val cswData: Seq[CswData] = cswDataProviderWithCSW.calculateForwardTransferCswData(Seq(refDataWithFTs), pubKeys, params)
    assertEquals("Different CSW data expected.", expectedCswData, cswData)
  }

  @Test
  def testUpdate(): Unit = {
    val blockId = new Array[Byte](32)
    Random.nextBytes(blockId)
    val blockVersion = new ByteArrayWrapper(blockId)


    val pubKeys: Set[SidechainTypes#SCP] = Set(
      getPrivateKey25519.publicImage(),
      getPrivateKey25519.publicImage(),
      getPrivateKey25519.publicImage()
    )

    val mockedSidechainWallet : SidechainWallet = mock[SidechainWallet]
    Mockito.when(mockedSidechainWallet.publicKeys()).thenReturn(pubKeys)

    val boxes: Seq[SidechainTypes#SCB] = Seq(
      getCustomBox.asInstanceOf[SidechainTypes#SCB], // non-coin box
      getZenBox,    // coin box
      getForgerBox, // coin box
      getCustomBox.asInstanceOf[SidechainTypes#SCB] // non-coin box
    )
    val mockedUtxoMerkleTreeView: UtxoMerkleTreeView = mock[UtxoMerkleTreeView]
    Mockito.when(mockedUtxoMerkleTreeView.utxoMerklePath(ArgumentMatchers.any[Array[Byte]]())).thenAnswer(args => {
      val boxId: Array[Byte] = args.getArgument(0)
      Some(boxIdToMerklePath(boxId))
    })

    val walletBoxes = boxes.map(box => new WalletBox(box, createdAt = 123456789L))
    val expectedCswData = boxes.withFilter(_.isInstanceOf[CoinsBox[_]]).map(b => walletBoxToCswData(b, mockedUtxoMerkleTreeView))

    Mockito.when(mockedSidechainWallet.boxes()).thenReturn(walletBoxes)

    val mockedBlock : SidechainBlock = mock[SidechainBlock]

    val walletFt1: ForwardTransfer = getForwardTransfer(pubKeys.head.asInstanceOf[PublicKey25519Proposition], params.sidechainId)
    val walletFt2: ForwardTransfer = getForwardTransfer(pubKeys.last.asInstanceOf[PublicKey25519Proposition], params.sidechainId)

    val ft1: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), params.sidechainId)
    val ft2: ForwardTransfer = getForwardTransfer(getPrivateKey25519.publicImage(), params.sidechainId)

    val mc2scTransactionsOutputs: Seq[SidechainRelatedMainchainOutput[_ <: Box[_ <: Proposition]]] = Seq(walletFt1, ft1, ft2, walletFt2)
    val aggTx = new MC2SCAggregatedTransaction(mc2scTransactionsOutputs.asJava, MC2SCAggregatedTransaction.MC2SC_AGGREGATED_TRANSACTION_VERSION)


    val refDataWithFTs: MainchainBlockReferenceData = MainchainBlockReferenceData(null, Some(aggTx), None, None, Seq(), None)

    val commTree = refDataWithFTs.commitmentTree(params.sidechainId, params.sidechainCreationVersion)
    Mockito.when(mockedBlock.mainchainBlockReferencesData).thenReturn(Seq(refDataWithFTs))

    val expectedFtCswDataList = Seq(
      ftToCswData(walletFt1, 0, commTree),
      ftToCswData(walletFt2, 3, commTree)
    )
    commTree.free()


    val expectedListOfCoinBoxes = expectedFtCswDataList ++ expectedCswData
    val mockedCswDataStorage: SidechainWalletCswDataStorage = mock[SidechainWalletCswDataStorage]

    Mockito.when(mockedCswDataStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[Int],
      ArgumentMatchers.any[Seq[CswData]]))
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        val withdrawalEpoch = answer.getArgument(1).asInstanceOf[Int]
        val cswData = answer.getArgument(2).asInstanceOf[Seq[CswData]]

        assertEquals("SidechainWalletCswDataStorage.update(...) actual version is wrong.",
          blockVersion, version)
        assertEquals("SidechainWalletCswDataStorage.update(...) withdrawalEpoch version is wrong.",
          withdrawalEpochNumber, withdrawalEpoch)
        assertEquals("SidechainWalletCswDataStorage.update(...) actual CswData is wrong.",
          expectedListOfCoinBoxes, cswData)
       Success(mockedCswDataStorage)
      })

    val cswDataProviderWithCSW = SidechainWalletCswDataProviderCSWEnabled(mockedCswDataStorage)

    cswDataProviderWithCSW.update(mockedBlock, blockVersion,withdrawalEpochNumber, params, mockedSidechainWallet, Option(mockedUtxoMerkleTreeView)) match {
      case Failure(t) => Assert.fail(s"Update failed for: ${t.getMessage}")
      case Success(_) =>
    }
  }


  def ftToCswData(ft: ForwardTransfer, leafIdx: Int, commitmentTree: SidechainCommitmentTree): CswData = {
    val scCommitmentMerklePath = commitmentTree.getSidechainCommitmentMerklePath(params.sidechainId).get
    val btrCommitment = commitmentTree.getBtrCommitment(params.sidechainId).get
    val certCommitment = commitmentTree.getCertCommitment(params.sidechainId).get
    val scCrCommitment = commitmentTree.getScCrCommitment(params.sidechainId).get
    val ftMerklePath = commitmentTree.getForwardTransferMerklePath(params.sidechainId, leafIdx).get

    ForwardTransferCswData(ft.getBox.id(), ft.getFtOutput.amount, ft.getFtOutput.propositionBytes, ft.getFtOutput.mcReturnAddress,
      ft.transactionHash(), ft.transactionIndex(), scCommitmentMerklePath, btrCommitment,
      certCommitment, scCrCommitment, ftMerklePath)
  }

  def walletBoxToCswData(box: SidechainTypes#SCB, view: UtxoMerkleTreeView): CswData = {
    UtxoCswData(box.id(), box.proposition().bytes, box.value(), box.nonce(),
      box.customFieldsHash(), view.utxoMerklePath(box.id()).get)
  }
}
