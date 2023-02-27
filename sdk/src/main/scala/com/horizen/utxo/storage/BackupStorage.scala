package com.horizen.utxo.storage

import com.horizen.storage.Storage
import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}
import com.horizen.utxo.backup.BoxIterator
import com.horizen.utxo.companion.SidechainBoxesCompanion

import java.util.{ArrayList => JArrayList}
import scala.util.Try

class BackupStorage (storage : Storage, val sidechainBoxesCompanion: SidechainBoxesCompanion) {
  // Version - random number
  // Key - byte array box Id
  // No remove operation

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  def update (version : ByteArrayWrapper, boxToSaveList : java.util.List[JPair[ByteArrayWrapper,ByteArrayWrapper]]) : Try[BackupStorage] = Try {
    require(boxToSaveList != null, "List of WalletBoxes to add/update must be NOT NULL.")
    require(!boxToSaveList.contains(null), "WalletBox to add/update must be NOT NULL.")
    require(!boxToSaveList.isEmpty, "List of WalletBoxes to add/update must be NOT EMPTY.")

    val removeList = new JArrayList[ByteArrayWrapper]()

    storage.update(version,
      boxToSaveList,
      removeList)

    this
  }

  def getBoxIterator: BoxIterator = new BoxIterator(storage.getIterator, sidechainBoxesCompanion)

  def isEmpty: Boolean = storage.isEmpty

  def close: Unit = storage.close()
}
