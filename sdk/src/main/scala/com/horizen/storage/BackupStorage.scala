package com.horizen.storage

import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}
import org.iq80.leveldb.DBIterator

import scala.util.Try
import java.util.{ArrayList => JArrayList}
import scorex.crypto.hash.Blake2b256

class BackupStorage (storage : Storage, sidechainBoxesCompanion: SidechainBoxesCompanion) {
  // Version - random number
  // Key - byte array box Id
  // No remove operation

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  def update (version : ByteArrayWrapper, boxToSaveList : java.util.List[JPair[ByteArrayWrapper,ByteArrayWrapper]]) : Try[BackupStorage] = Try {
    require(boxToSaveList != null, "List of WalletBoxes to add/update must be NOT NULL. Use empty List instead.")
    require(!boxToSaveList.contains(null), "WalletBox to add/update must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()

    storage.update(version,
      boxToSaveList,
      removeList)

    this
  }

  def calculateKey(boxId : Array[Byte]) : ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
  }

  def getIterator: DBIterator = storage.getIterator

  def isEmpty: Boolean = storage.isEmpty

  def close: Unit = storage.close()
}
