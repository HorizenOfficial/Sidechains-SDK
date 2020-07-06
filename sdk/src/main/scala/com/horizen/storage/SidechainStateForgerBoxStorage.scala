package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.utils.ByteArrayWrapper
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging
import java.util.{ArrayList => JArrayList}

import com.horizen.box.ForgerBox
import com.horizen.utils.{Pair => JPair}

import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class SidechainStateForgerBoxStorage(storage: Storage, sidechainBoxesCompanion: SidechainBoxesCompanion)
    extends ScorexLogging
    with SidechainTypes
{
  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")


  def calculateKey(boxId: Array[Byte]): ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
  }

  def getForgerBox(boxId: Array[Byte]): Option[ForgerBox] = {
    storage.get(calculateKey(boxId)).asScala match {
      case Some(baw) =>
        sidechainBoxesCompanion.parseBytesTry(baw.data) match {
          case Success(box) => Option(box.asInstanceOf[ForgerBox])
          case Failure(exception) =>
            log.error("Error while WalletBox parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getAllForgerBoxes: Seq[ForgerBox] = {
    storage.getAll
      .asScala
      .map(pair => sidechainBoxesCompanion.parseBytes(pair.getValue.data).asInstanceOf[ForgerBox])
  }

  def update(version: ByteArrayWrapper,
             forgerBoxUpdateSeq: Seq[ForgerBox],
             boxIdsRemoveSet: Set[ByteArrayWrapper]): Try[SidechainStateForgerBoxStorage] = Try {
    require(forgerBoxUpdateSeq != null, "List of ForgerBoxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveSet != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")


    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    // Update boxes data
    for (r <- boxIdsRemoveSet)
      removeList.add(calculateKey(r.data))

    for (b <- forgerBoxUpdateSeq)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](calculateKey(b.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))))

    storage.update(version, updateList, removeList)

    this
  }

  def lastVersionId: Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }


  def rollback(version: ByteArrayWrapper): Try[SidechainStateForgerBoxStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}
