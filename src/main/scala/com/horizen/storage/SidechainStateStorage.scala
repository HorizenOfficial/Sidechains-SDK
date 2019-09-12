package com.horizen.storage

import java.util.Optional
import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.SidechainTypes
import javafx.util.{Pair => JPair}

import scala.util._
import scala.collection.JavaConverters._
import com.horizen.box.Box
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.proposition.Proposition
import com.horizen.utils.ByteArrayWrapper
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

class SidechainStateStorage (storage : Storage, sidechainBoxesCompanion: SidechainBoxesCompanion)
  extends ScorexLogging
  with SidechainTypes
{
  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  def calculateKey(boxId : Array[Byte]) : ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
  }

  def get(boxId : Array[Byte]) : Option[SidechainTypes#SCB] = {
    storage.get(calculateKey(boxId)) match {
      case v if v.isPresent => {
        sidechainBoxesCompanion.parseBytesTry(v.get().data) match {
          case Success(box) => Option(box)
          case Failure(exception) => {
            log.error("Error while WalletBox parsing.", exception)
            Option.empty
          }
        }
      }
      case _ => Option.empty
    }
  }

  def update(version : ByteArrayWrapper, boxUpdateList : Set[SidechainTypes#SCB],
             boxIdsRemoveList : Set[Array[Byte]]) : Try[SidechainStateStorage] = Try {
    require(boxUpdateList != null, "List of Boxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveList != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    require(!boxUpdateList.contains(null), "Box to add/update must be NOT NULL.")
    require(!boxIdsRemoveList.contains(null), "BoxId to remove must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    for (r <- boxIdsRemoveList)
      removeList.add(calculateKey(r))

    for (b <- boxUpdateList)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](calculateKey(b.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))))

    storage.update(version,
      updateList,
      removeList)

    this
  }

  def lastVersionId : Option[ByteArrayWrapper] = {
    val lastVersion = storage.lastVersionID()
    if (lastVersion.isPresent)
      Some(lastVersion.get())
    else
      None
  }

  def rollbackVersions : Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Try[SidechainStateStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty

}
