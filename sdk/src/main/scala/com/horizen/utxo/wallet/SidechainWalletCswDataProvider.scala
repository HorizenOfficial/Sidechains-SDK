package com.horizen.utxo.wallet

import com.horizen.SidechainTypes
import com.horizen.block.MainchainBlockReferenceData
import com.horizen.params.NetworkParams
import com.horizen.proposition.PublicKey25519Proposition
import com.horizen.storage.SidechainStorageInfo
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.utils.ByteArrayWrapper
import com.horizen.utxo.block.SidechainBlock
import com.horizen.utxo.box.CoinsBox
import com.horizen.utxo.storage.SidechainWalletCswDataStorage
import com.horizen.utxo.utils.{CswData, ForwardTransferCswData, UtxoCswData}
import com.horizen.utxo.state.UtxoMerkleTreeView

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

trait SidechainWalletCswDataProvider extends SidechainStorageInfo {

  def rollback(version: ByteArrayWrapper): Try[SidechainWalletCswDataProvider]

  def update(modifier: SidechainBlock,
             version: ByteArrayWrapper,
             withdrawalEpoch: Int,
             params: NetworkParams,
             wallet: SidechainWallet,
             utxoMerkleTreeViewOpt: Option[UtxoMerkleTreeView]): Try[SidechainWalletCswDataProvider]

  def getCswData(withdrawalEpoch: Int): Seq[CswData]

  override def getStorageName: String = "SidechainWalletCswDataStorage"
}

case class SidechainWalletCswDataProviderCSWEnabled(private val sidechainWalletCswDataStorage: SidechainWalletCswDataStorage) extends  SidechainWalletCswDataProvider {

  override def lastVersionId: Option[ByteArrayWrapper] = {
    sidechainWalletCswDataStorage.lastVersionId
  }

  override def rollback(version: ByteArrayWrapper): Try[SidechainWalletCswDataProvider] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    SidechainWalletCswDataProviderCSWEnabled(sidechainWalletCswDataStorage.rollback(version).get)
  }

  override def update(modifier: SidechainBlock,
                      version: ByteArrayWrapper,
                      withdrawalEpoch: Int,
                      params: NetworkParams,
                      wallet: SidechainWallet,
                      utxoMerkleTreeViewOpt: Option[UtxoMerkleTreeView]): Try[SidechainWalletCswDataProvider] = Try {
    //Here we already updated the wallet with the newBoxes inside the SidechainBlock, so we also include FeePaymentBoxes in the CSW calculation
    val utxoCswData: Seq[CswData] = utxoMerkleTreeViewOpt.map(view => calculateUtxoCswData(view, wallet.boxes())).getOrElse(Seq())
    val ftCswData = calculateForwardTransferCswData(modifier.mainchainBlockReferencesData, wallet.publicKeys(), params)

    val triedCswDataStorage = sidechainWalletCswDataStorage.update(version, withdrawalEpoch, ftCswData ++ utxoCswData)
    SidechainWalletCswDataProviderCSWEnabled(triedCswDataStorage.get)
  }

  override def getCswData(withdrawalEpoch: Int): Seq[CswData] = {
    sidechainWalletCswDataStorage.getCswData(withdrawalEpoch)
  }

  private[horizen] def calculateUtxoCswData(view: UtxoMerkleTreeView, boxes: Seq[WalletBox]): Seq[CswData] = {
    boxes.withFilter(wb => wb.box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]]).map(wb => {
      val box = wb.box
      UtxoCswData(box.id(), box.proposition().bytes, box.value(), box.nonce(),
        box.customFieldsHash(), view.utxoMerklePath(box.id()).get)
    })
  }

  private[horizen] def calculateForwardTransferCswData(mcBlockRefDataSeq: Seq[MainchainBlockReferenceData], pubKeys: Set[SidechainTypes#SCP], params: NetworkParams): Seq[CswData] = {
    val ftCswDataList = ListBuffer[CswData]()

    mcBlockRefDataSeq.foreach(mcBlockRefData => {
      // If MC2SCAggTx is present -> collect wallet related FTs
      mcBlockRefData.sidechainRelatedAggregatedTransaction.foreach(aggTx => {
        var ftLeafIdx: Int = -1
        val walletFTs: Seq[(ForwardTransfer, Int)] = aggTx.mc2scTransactionsOutputs().asScala.flatMap(_ match {
          case _: SidechainCreation => None// No CSW support for ScCreation outputs as FT
          case ft: ForwardTransfer =>
            ftLeafIdx += 1
            if(pubKeys.contains(ft.getBox.proposition()))
              Some((ft, ftLeafIdx))
            else
              None
        })

        if(walletFTs.nonEmpty) {
          val commitmentTree = mcBlockRefData.commitmentTree(params.sidechainId, params.sidechainCreationVersion)
          val scCommitmentMerklePath = commitmentTree.getSidechainCommitmentMerklePath(params.sidechainId).get
          val btrCommitment = commitmentTree.getBtrCommitment(params.sidechainId).get
          val certCommitment = commitmentTree.getCertCommitment(params.sidechainId).get
          val scCrCommitment = commitmentTree.getScCrCommitment(params.sidechainId).get

          for((ft: ForwardTransfer, leafIdx: Int) <- walletFTs) {
            val ftMerklePath = commitmentTree.getForwardTransferMerklePath(params.sidechainId, leafIdx).get
            ftCswDataList.append(
              ForwardTransferCswData(ft.getBox.id(), ft.getFtOutput.amount, ft.getFtOutput.propositionBytes,
                ft.getFtOutput.mcReturnAddress, ft.transactionHash(), ft.transactionIndex(), scCommitmentMerklePath,
                btrCommitment, certCommitment, scCrCommitment, ftMerklePath)
            )
          }
          commitmentTree.free()
        }
      })
    })

    ftCswDataList
  }
}

case class SidechainWalletCswDataProviderCSWDisabled() extends  SidechainWalletCswDataProvider {

  override def lastVersionId: Option[ByteArrayWrapper] = None

  override def rollback(version: ByteArrayWrapper): Try[SidechainWalletCswDataProvider] = Try {
    this
  }

  override def update(modifier: SidechainBlock,
                      version: ByteArrayWrapper,
                      withdrawalEpoch: Int,
                      params: NetworkParams,
                      wallet: SidechainWallet,
                      utxoMerkleTreeViewOpt: Option[UtxoMerkleTreeView]): Try[SidechainWalletCswDataProvider] = Try {
    this
  }


  override def getCswData(withdrawalEpoch: Int): Seq[CswData] = Seq()
}