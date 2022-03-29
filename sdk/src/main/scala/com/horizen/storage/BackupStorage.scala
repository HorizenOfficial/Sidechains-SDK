package com.horizen.storage

import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.{SidechainTypes}
import com.horizen.utils.ByteArrayWrapper
import org.iq80.leveldb.DBIterator

import scala.util.Try
import java.util.{ArrayList => JArrayList}
import com.horizen.utils.{Pair => JPair}
import scorex.crypto.hash.Blake2b256

class BackupStorage (storage : Storage, sidechainBoxesCompanion: SidechainBoxesCompanion) {
  // Version - random number
  // Key - byte array box Id
  // No remove operation

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  def update (version : ByteArrayWrapper, boxToSaveList : java.util.List[SidechainTypes#SCB],
              boxIdsRemoveList : java.util.List[Array[Byte]]) : Try[BackupStorage] = Try {
    require(boxToSaveList != null, "List of WalletBoxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveList != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    require(!boxToSaveList.contains(null), "WalletBox to add/update must be NOT NULL.")
    require(!boxIdsRemoveList.contains(null), "BoxId to remove must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    boxToSaveList.forEach(b => {
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](calculateKey(b.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))))
    })
    System.out.println("UPDATE LIST SIZE "+updateList.size()+" VERSION "+version+" REMOVE LIST "+removeList.size())
    storage.update(version,
      updateList,
      removeList)

    this
  }

  def calculateKey(boxId : Array[Byte]) : ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
  }

  def getIterator: DBIterator = storage.getIterator

  def isEmpty: Boolean = storage.isEmpty
}
