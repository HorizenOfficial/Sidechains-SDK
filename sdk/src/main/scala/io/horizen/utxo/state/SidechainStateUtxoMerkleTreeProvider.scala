package io.horizen.utxo.state

import io.horizen.SidechainTypes
import io.horizen.proposition.PublicKey25519Proposition
import io.horizen.storage.SidechainStorageInfo
import io.horizen.utils.ByteArrayWrapper
import io.horizen.utxo.box.CoinsBox
import io.horizen.utxo.storage.SidechainStateUtxoMerkleTreeStorage

import scala.util.Try

trait SidechainStateUtxoMerkleTreeProvider extends SidechainStorageInfo {
  def rollback(version: ByteArrayWrapper): Try[SidechainStateUtxoMerkleTreeProvider]

  def getMerklePath(boxId: Array[Byte]): Option[Array[Byte]]

  def update(version: ByteArrayWrapper,
             boxesToAppend: Seq[SidechainTypes#SCB],
             boxesToRemoveSet: Set[ByteArrayWrapper]): Try[SidechainStateUtxoMerkleTreeProvider]

  def getMerkleTreeRoot: Option[Array[Byte]]

  override def getStorageName: String = "SidechainStateUtxoMerkleTreeStorage"
}

case class SidechainUtxoMerkleTreeProviderCSWEnabled(private val utxoMerkleTreeStorage: SidechainStateUtxoMerkleTreeStorage) extends SidechainStateUtxoMerkleTreeProvider{

  override def rollback(version: ByteArrayWrapper): Try[SidechainStateUtxoMerkleTreeProvider] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    SidechainUtxoMerkleTreeProviderCSWEnabled(utxoMerkleTreeStorage.rollback(version).get)
  }

  override def lastVersionId: Option[ByteArrayWrapper] = {
    utxoMerkleTreeStorage.lastVersionId
  }

  override def getMerklePath(boxId: Array[Byte]): Option[Array[Byte]] = {
    utxoMerkleTreeStorage.getMerklePath(boxId)
  }

  override def update(version: ByteArrayWrapper,
                      boxesToAppend: Seq[SidechainTypes#SCB],
                      boxesToRemoveSet: Set[ByteArrayWrapper]): Try[SidechainStateUtxoMerkleTreeProvider] = Try {
    require(boxesToAppend != null, "List of boxes to add must be NOT NULL. Use empty List instead.")
    require(boxesToRemoveSet != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    val coinBoxesToAppend = boxesToAppend.filter(box => box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]])

    SidechainUtxoMerkleTreeProviderCSWEnabled(utxoMerkleTreeStorage.update(version, coinBoxesToAppend, boxesToRemoveSet).get)
  }

  override def getMerkleTreeRoot: Option[Array[Byte]] = Some(utxoMerkleTreeStorage.getMerkleTreeRoot)
}

case class SidechainUtxoMerkleTreeProviderCSWDisabled() extends SidechainStateUtxoMerkleTreeProvider{

  override def rollback(version: ByteArrayWrapper): Try[SidechainStateUtxoMerkleTreeProvider] = Try {
    this
  }

  override def lastVersionId: Option[ByteArrayWrapper] = None

  override def getMerklePath(boxId: Array[Byte]): Option[Array[Byte]] = None

  override def update(version: ByteArrayWrapper,
                      boxesToAppend: Seq[SidechainTypes#SCB],
                      boxesToRemoveSet: Set[ByteArrayWrapper]): Try[SidechainStateUtxoMerkleTreeProvider] = Try {

    this
  }

  override def getMerkleTreeRoot: Option[Array[Byte]] = None
}