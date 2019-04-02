package com.horizen.storage

import java.util.{Optional, ArrayList => JArrayList}

import javafx.util.{Pair => JPair}
import com.horizen.utils.ByteArrayWrapper
import com.horizen.{WalletBox, WalletBoxSerializer}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.collection.mutable

class SidechainWalletBoxStorage (storage : Storage)(sidechainBoxesCompanion: SidechainBoxesCompanion)
  extends ScorexLogging
{
  // Version - block Id
  // Key - byte array box Id
  // No remove operation

  type B <: Box[_]
  type BoxClass = Class[B]

  private val _walletBoxes = new mutable.LinkedHashMap[Array[Byte], WalletBox]()
  private val _walletBoxesByType = new mutable.LinkedHashMap[BoxClass, mutable.Map[Array[Byte], WalletBox]]()
  private val _walletBoxesAmount = new mutable.LinkedHashMap[BoxClass, Long]()
  private val _walletBoxSerializer = new WalletBoxSerializer(sidechainBoxesCompanion)

  private def calculateBoxesAmounts() : Unit = {
    for (bc <-_walletBoxesByType.keys)
      _walletBoxesAmount.put(bc, _walletBoxesByType(bc).map(_._2.box.value()).sum)
  }

  private def updateWalletBoxByType(walletBox : WalletBox) : Unit = {
    val bc = walletBox.box.getClass.asInstanceOf[BoxClass]
    val t = _walletBoxesByType.get(bc)
    if (t.isEmpty) {
      val m = new mutable.LinkedHashMap[Array[Byte], WalletBox]()
      m.put(walletBox.box.id(), walletBox)
      _walletBoxesByType.put(bc, m)
    } else
      t.get.put(walletBox.box.id(), walletBox)
  }

  private def removeWalletBoxByType(boxIdToRemove : Array[Byte]) : Unit = {
    for (bc <- _walletBoxesByType.keys)
      _walletBoxesByType(bc).remove(boxIdToRemove)
  }

  private def loadWalletBoxes() : Unit = {
    _walletBoxes.clear()
    _walletBoxesByType.clear()
    for (wb <- storage.getAll.asScala){
      val walletBox = _walletBoxSerializer.parseBytes(wb.getValue.data)
      if (walletBox.isSuccess) {
        _walletBoxes.put(walletBox.get.box.id(), walletBox.get)
        updateWalletBoxByType(walletBox.get)
      }
    }
    calculateBoxesAmounts()
  }

  def get (boxId : Array[Byte]) : Option[WalletBox] = {
    _walletBoxes.get(boxId)
  }

  def get (boxIds : List[Array[Byte]]) : List[WalletBox] = {
    _walletBoxes.values
      .filter((wb : WalletBox) => boxIds.contains(wb.box.id()))
      .toList
  }

  def getAll : List[WalletBox] = {
    _walletBoxes.values.toList
  }

  def getByType (boxType: Class[_ <: Box[_ <: Proposition]]) : List[WalletBox] = {
    _walletBoxesByType(boxType.asInstanceOf[BoxClass]).values.toList
  }

  def getBoxAmount (boxType: Class[_ <: Box[_ <: Proposition]]): Long = {
    _walletBoxesAmount(boxType.asInstanceOf[BoxClass])
  }

  def update (version : Array[Byte], walletBoxToUpdate : WalletBox, boxIdToRemove : Array[Byte]) : Unit = {
    val keyToUpdate = new ByteArrayWrapper(walletBoxToUpdate.box.id())
    val valueToUpdate = new ByteArrayWrapper(_walletBoxSerializer.toBytes(walletBoxToUpdate))

    storage.update(new ByteArrayWrapper(version),
      List(new ByteArrayWrapper(boxIdToRemove)).asJava,
      List(new JPair(keyToUpdate, valueToUpdate)).asJava)

    _walletBoxes.put(walletBoxToUpdate.box.id(), walletBoxToUpdate)
    updateWalletBoxByType(walletBoxToUpdate)

    _walletBoxes.remove(boxIdToRemove)
    removeWalletBoxByType(boxIdToRemove)

    calculateBoxesAmounts()
  }

  def update (version : Array[Byte], walletBoxUpdateList : List[WalletBox], boxIdsRemoveList : List[Array[Byte]]) : Unit = {
    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    for (b <- boxIdsRemoveList) {
      removeList.add(new ByteArrayWrapper(b))
      _walletBoxes.remove(b)
      removeWalletBoxByType(b)
    }

    for (b <- walletBoxUpdateList) {
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper(b.box.id()),
        new ByteArrayWrapper(_walletBoxSerializer.toBytes(b))))
      _walletBoxes.put(b.box.id(), b)
      updateWalletBoxByType(b)
    }

    storage.update(new ByteArrayWrapper(version),
      removeList,
      updateList)

    calculateBoxesAmounts()
  }

  def lastVesrionId : Optional[ByteArrayWrapper] = {
    storage.lastVersionID()
  }

  def rollbackVersions : List[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Unit = {
    storage.rollback(version)
    loadWalletBoxes()
    calculateBoxesAmounts()
  }

}
