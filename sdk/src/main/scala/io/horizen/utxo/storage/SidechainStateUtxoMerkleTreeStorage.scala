package io.horizen.utxo.storage

import io.horizen.SidechainTypes
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.cryptolibprovider.utils.InMemorySparseMerkleTreeWrapper
import com.horizen.librustsidechains.FieldElement
import io.horizen.storage.{SidechainStorageInfo, Storage}
import io.horizen.utils.{ByteArrayWrapper, Utils, Pair => JPair}
import io.horizen.utxo.utils.{UtxoMerkleTreeLeafInfo, UtxoMerkleTreeLeafInfoSerializer}
import sparkz.util.SparkzLogging

import java.util.{List => JList}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}

class SidechainStateUtxoMerkleTreeStorage(storage: Storage)
  extends SparkzLogging with SidechainStorageInfo with SidechainTypes {

  var merkleTreeWrapper: InMemorySparseMerkleTreeWrapper = loadMerkleTree()

  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")

  private def loadMerkleTree(): InMemorySparseMerkleTreeWrapper = {
    val treeHeight: Int = CryptoLibProvider.cswCircuitFunctions.utxoMerkleTreeHeight()
    val merkleTree = new InMemorySparseMerkleTreeWrapper(treeHeight)

    val newLeaves: Map[java.lang.Long, FieldElement] = getAllLeavesInfo.map(leafInfo => {
      long2Long(leafInfo.position) -> FieldElement.deserialize(leafInfo.leaf)
    }).toMap

    try {
      merkleTree.addLeaves(newLeaves.asJava)
    } finally {
      newLeaves.foreach(_._2.close())
    }

    merkleTree
  }

  private[horizen] def calculateLeaf(box: SidechainTypes#SCB): FieldElement = {
    CryptoLibProvider.cswCircuitFunctions.getUtxoMerkleTreeLeaf(box)
  }


  def getLeafInfo(boxId: Array[Byte]): Option[UtxoMerkleTreeLeafInfo] = {
    storage.get(Utils.calculateKey(boxId)) match {
      case v if v.isPresent =>
        UtxoMerkleTreeLeafInfoSerializer.parseBytesTry(v.get().data) match {
          case Success(leafInfo) => Option(leafInfo)
          case Failure(exception) =>
            log.error("Error while UtxoMerkleTreeLeafInfo parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getMerklePath(boxId: Array[Byte]): Option[Array[Byte]] = {
    getLeafInfo(boxId).map(leafInfo => merkleTreeWrapper.merklePath(leafInfo.position))
  }

  private[horizen] def getAllLeavesInfo: Seq[UtxoMerkleTreeLeafInfo] = {
    storage.getAll
      .asScala
      .map(pair => UtxoMerkleTreeLeafInfoSerializer.parseBytes(pair.getValue.data))
  }

  def getMerkleTreeRoot: Array[Byte] = merkleTreeWrapper.calculateRoot()

  def update(version: ByteArrayWrapper,
             boxesToAppend: Seq[SidechainTypes#SCB],
             boxesToRemoveSet: Set[ByteArrayWrapper]): Try[SidechainStateUtxoMerkleTreeStorage] = Try {
    require(boxesToAppend != null, "List of boxes to add must be NOT NULL. Use empty List instead.")
    require(boxesToRemoveSet != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")

    val removeList: JList[ByteArrayWrapper] = boxesToRemoveSet.map(id => Utils.calculateKey(id.data)).toList.asJava

    // Remove leaves from inmemory tree
    require(merkleTreeWrapper.removeLeaves(boxesToRemoveSet.flatMap(id => {
      getLeafInfo(id.data).map(leafInfo => leafInfo.position)
    }).toArray), "Failed to remove leaves from UtxoMerkleTree")

    // Collect positions for the new leaves and check that there is enough empty space in the tree
    val newLeavesPositions: Seq[Long] = merkleTreeWrapper.leftmostEmptyPositions(boxesToAppend.size).asScala.map(Long2long)
    if (newLeavesPositions.size != boxesToAppend.size) {
      throw new IllegalStateException("Not enough empty leaves in the UTXOMerkleTree.")
    }

    val leavesToAppend = boxesToAppend.map(box => (Utils.calculateKey(box.id()), calculateLeaf(box))).zip(newLeavesPositions)

    // Add leaves to inmemory tree
    require(merkleTreeWrapper.addLeaves(leavesToAppend.map {
      case ((_, leaf: FieldElement), position: Long) => long2Long(position) -> leaf
    }.toMap.asJava), "Failed to add leaves to UtxoMerkleTree")

    val updateList: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = leavesToAppend.map {
      case ((key: ByteArrayWrapper, leaf: FieldElement), position: Long) =>
        val leafBytes = leaf.serializeFieldElement()
        leaf.freeFieldElement()

        new JPair[ByteArrayWrapper, ByteArrayWrapper](
          key,
          new ByteArrayWrapper(UtxoMerkleTreeLeafInfo(leafBytes, position).bytes)
        )
    }.asJava

    storage.update(version, updateList, removeList)

    this
  }.recoverWith {
    case exception =>
      // Reload merkle tree in case of any exception to restore the proper state.
      merkleTreeWrapper.close()
      merkleTreeWrapper = loadMerkleTree()
      Failure(exception)
  }

  override def lastVersionId: Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions(): Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollbackVersions(maxNumberOfVersions: Int): List[ByteArrayWrapper] = {
    storage.rollbackVersions(maxNumberOfVersions).asScala.toList
  }

  def rollback(version: ByteArrayWrapper): Try[SidechainStateUtxoMerkleTreeStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    // Reload merkle tree
    merkleTreeWrapper.close()
    merkleTreeWrapper = loadMerkleTree()
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}
