package io.horizen.utxo.storage

import io.horizen.SidechainTypes
import io.horizen.storage.{SidechainStorageInfo, Storage}
import io.horizen.utils.{ByteArrayWrapper, Utils, Pair => JPair}
import io.horizen.utxo.box.{ForgerBox, ForgerBoxSerializer}
import sparkz.util.SparkzLogging

import java.util.{ArrayList => JArrayList}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}

class SidechainStateForgerBoxStorage(storage: Storage)
    extends SparkzLogging
      with SidechainStorageInfo
      with SidechainTypes
{
  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")

  private val forgerBoxSerializer: ForgerBoxSerializer = ForgerBoxSerializer.getSerializer


  def getForgerBox(boxId: Array[Byte]): Option[ForgerBox] = {
    storage.get(Utils.calculateKey(boxId)).asScala match {
      case Some(baw) =>
        forgerBoxSerializer.parseBytesTry(baw.data) match {
          case Success(box) => Option(box)
          case Failure(exception) =>
            log.error("SidechainStateForgerBoxStorage: Error while ForgerBox parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getAllForgerBoxes: Seq[ForgerBox] = {
    storage.getAll
      .asScala
      .map(pair => forgerBoxSerializer.parseBytes(pair.getValue.data))
  }

  def update(version: ByteArrayWrapper,
             forgerBoxUpdateSeq: Seq[ForgerBox],
             boxIdsRemoveSet: Set[ByteArrayWrapper]): Try[SidechainStateForgerBoxStorage] = Try {
    require(forgerBoxUpdateSeq != null, "List of ForgerBoxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveSet != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    // Update boxes data
    for (id <- boxIdsRemoveSet)
      removeList.add(Utils.calculateKey(id.data))

    for (box <- forgerBoxUpdateSeq)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](Utils.calculateKey(box.id()),
        new ByteArrayWrapper(forgerBoxSerializer.toBytes(box))))

    storage.update(version, updateList, removeList)

    this
  }

  override def lastVersionId: Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions: Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback(version: ByteArrayWrapper): Try[SidechainStateForgerBoxStorage] = Try {
    require(version != null, "SidechainStateForgerBoxStorage: Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}
