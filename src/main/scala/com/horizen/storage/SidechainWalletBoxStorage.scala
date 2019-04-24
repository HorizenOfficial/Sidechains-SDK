package com.horizen.storage

import java.util.{Optional, ArrayList => JArrayList}
import javafx.util.{Pair => JPair}

import com.horizen.utils.ByteArrayWrapper
import com.horizen.{WalletBox, WalletBoxSerializer}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.box.Box
import com.horizen.proposition.Proposition

import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

class SidechainWalletBoxStorage (storage : Storage, sidechainBoxesCompanion: SidechainBoxesCompanion)
  extends ScorexLogging
{
  // Version - block Id
  // Key - byte array box Id
  // No remove operation

  type BoxClass = Class[_ <: Box[_ <: Proposition]]

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  private val _walletBoxes = new mutable.LinkedHashMap[ByteArrayWrapper, WalletBox]()
  private val _walletBoxesByType = new mutable.LinkedHashMap[BoxClass, mutable.Map[ByteArrayWrapper, WalletBox]]()
  private val _walletBoxesBalances = new mutable.LinkedHashMap[BoxClass, Long]()
  private val _walletBoxSerializer = new WalletBoxSerializer(sidechainBoxesCompanion)

  private def calculateKey(boxId : Array[Byte]) : ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
  }

  private def calculateBoxesBalances() : Unit = {
    for (bc <-_walletBoxesByType.keys)
      _walletBoxesBalances.put(bc, _walletBoxesByType(bc).map(_._2.box.value()).sum)
  }

  private def updateBoxesBalance (boxToAdd : WalletBox, boxToRemove : WalletBox) : Unit = {
    if (boxToAdd != null) {
      val bca = boxToAdd.box.getClass.asInstanceOf[BoxClass]
      _walletBoxesBalances.put(bca, _walletBoxesBalances.getOrElse(bca, 0L) + boxToAdd.box.value())
    }
    if (boxToRemove != null) {
      val bcr = boxToRemove.box.getClass.asInstanceOf[BoxClass]
      _walletBoxesBalances.put(bcr, _walletBoxesBalances.getOrElse(bcr, 0L) - boxToRemove.box.value())
    }
  }

  private def updateWalletBoxByType(walletBox : WalletBox) : Unit = {
    val bc = walletBox.box.getClass.asInstanceOf[BoxClass]
    val key = calculateKey(walletBox.box.id())
    val t = _walletBoxesByType.get(bc)
    if (t.isEmpty) {
      val m = new mutable.LinkedHashMap[ByteArrayWrapper, WalletBox]()
      m.put(key, walletBox)
      _walletBoxesByType.put(bc, m)
    } else
      t.get.put(key, walletBox)
  }

  private def removeWalletBoxByType(boxIdToRemove : ByteArrayWrapper) : Unit = {
    for (bc <- _walletBoxesByType.keys)
      _walletBoxesByType(bc).remove(boxIdToRemove)
  }

  private def loadWalletBoxes() : Unit = {
    _walletBoxes.clear()
    _walletBoxesByType.clear()
    for (wb <- storage.getAll.asScala){
      val walletBox = _walletBoxSerializer.parseBytes(wb.getValue.data)
      if (walletBox.isSuccess) {
        _walletBoxes.put(calculateKey(walletBox.get.box.id()), walletBox.get)
        updateWalletBoxByType(walletBox.get)
      } else
        log.error("Error while WalletBox parsing.", walletBox)
    }
    calculateBoxesBalances()
  }

  loadWalletBoxes()

  def get (boxId : Array[Byte]) : Option[WalletBox] = {
    _walletBoxes.get(calculateKey(boxId))
  }

  def get (boxIds : List[Array[Byte]]) : List[WalletBox] = {
    for (id <- boxIds.map(calculateKey(_)) if _walletBoxes.get(id).isDefined) yield _walletBoxes(id)
  }

  def getAll : List[WalletBox] = {
    _walletBoxes.values.toList
  }

  def getByType (boxType: Class[_ <: Box[_ <: Proposition]]) : List[WalletBox] = {
    _walletBoxesByType(boxType).values.toList
  }

  def getBoxesBalance (boxType: Class[_ <: Box[_ <: Proposition]]): Long = {
    _walletBoxesBalances(boxType)
  }

  def update (version : Array[Byte], walletBoxUpdateList : List[WalletBox],
              boxIdsRemoveList : List[Array[Byte]]) : Try[SidechainWalletBoxStorage] = Try {
    require(!walletBoxUpdateList.contains(null), "WalletBox to add/update must be NOT NULL.")
    require(!boxIdsRemoveList.contains(null), "BoxId to remove must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    for (b <- boxIdsRemoveList) {
      val key = calculateKey(b)
      removeList.add(new ByteArrayWrapper(key))
      val btr = _walletBoxes.remove(key)
      removeWalletBoxByType(key)
      if (btr.isDefined)
        updateBoxesBalance(null, btr.get)
    }

    for (b <- walletBoxUpdateList) {
      val key = calculateKey(b.box.id())
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](key,
        new ByteArrayWrapper(_walletBoxSerializer.toBytes(b))))
      val bta = _walletBoxes.put(key, b)
      updateWalletBoxByType(b)
      if (bta.isEmpty)
        updateBoxesBalance(b, null)
    }

    storage.update(new ByteArrayWrapper(version),
      removeList,
      updateList)

    this
  }

  def lastVesrionId : Optional[ByteArrayWrapper] = {
    storage.lastVersionID()
  }

  def rollbackVersions : List[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Try[SidechainWalletBoxStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    loadWalletBoxes()
    this
  }

}
