package io.horizen.utxo.storage

import io.horizen.SidechainTypes
import io.horizen.proposition.Proposition
import io.horizen.storage.{SidechainStorageInfo, Storage, StorageIterator}
import io.horizen.utils.{ByteArrayWrapper, Utils, Pair => JPair}
import io.horizen.utxo.box.Box
import io.horizen.utxo.companion.SidechainBoxesCompanion
import io.horizen.utxo.wallet.{WalletBox, WalletBoxSerializer}
import sparkz.util.SparkzLogging

import java.util.{ArrayList => JArrayList}
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.util.Try

class SidechainWalletBoxStorage (storage : Storage, sidechainBoxesCompanion: SidechainBoxesCompanion)
  extends SidechainTypes
    with SidechainStorageInfo
    with SparkzLogging
{
  // Version - block Id
  // Key - byte array box Id
  // No remove operation

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  private val _walletBoxes = new mutable.LinkedHashMap[ByteArrayWrapper, WalletBox]()
  private val _walletBoxesByType = new mutable.LinkedHashMap[Class[_ <: Box[_ <: Proposition]], mutable.Map[ByteArrayWrapper, WalletBox]]()
  private val _walletBoxesBalances = new mutable.LinkedHashMap[Class[_ <: Box[_ <: Proposition]], Long]()
  private val _walletBoxSerializer = new WalletBoxSerializer(sidechainBoxesCompanion)

  loadWalletBoxes()

  private def calculateBoxesBalances() : Unit = {
    for (bc <-_walletBoxesByType.keys)
      _walletBoxesBalances.put(bc, _walletBoxesByType(bc).map(_._2.box.value()).sum)
  }

  private def updateBoxesBalance (boxToAdd : WalletBox, boxToRemove : WalletBox) : Unit = {
    if (boxToAdd != null) {
      val bca = boxToAdd.box.getClass
      _walletBoxesBalances.put(bca, _walletBoxesBalances.getOrElse(bca, 0L) + boxToAdd.box.value())
    }
    if (boxToRemove != null) {
      val bcr = boxToRemove.box.getClass
      _walletBoxesBalances.put(bcr, _walletBoxesBalances.getOrElse(bcr, 0L) - boxToRemove.box.value())
    }
  }

  private def addWalletBoxByType(walletBox : WalletBox) : Unit = {
    val bc = walletBox.box.getClass
    val key = Utils.calculateKey(walletBox.box.id())
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
      val walletBox = _walletBoxSerializer.parseBytesTry(wb.getValue.data)
      if (walletBox.isSuccess) {
        _walletBoxes.put(Utils.calculateKey(walletBox.get.box.id()), walletBox.get)
        addWalletBoxByType(walletBox.get)
      } else
        log.error("Error while WalletBox parsing.", walletBox)
    }
    calculateBoxesBalances()
  }

  def get (boxId : Array[Byte]) : Option[WalletBox] = {
    _walletBoxes.get(Utils.calculateKey(boxId))
  }

  def get (boxIds : List[Array[Byte]]) : List[WalletBox] = {
    for (id <- boxIds.map(Utils.calculateKey) if _walletBoxes.get(id).isDefined) yield _walletBoxes(id)
  }

  def getAll : List[WalletBox] = {
    _walletBoxes.values.toList
  }

  def getByType (boxType: Class[_ <: Box[_ <: Proposition]]) : List[WalletBox] = {
    _walletBoxesByType.get(boxType) match {
      case Some(v) => v.values.toList
      case None => List[WalletBox]()
    }
  }

  def getBoxesBalance (boxType: Class[_ <: Box[_ <: Proposition]]): Long = {
    _walletBoxesBalances.getOrElse(boxType, 0L)
  }

  def update (version : ByteArrayWrapper, walletBoxUpdateList : List[WalletBox],
              boxIdsRemoveList : List[Array[Byte]]) : Try[SidechainWalletBoxStorage] = Try {
    require(walletBoxUpdateList != null, "List of WalletBoxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveList != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    require(!walletBoxUpdateList.contains(null), "WalletBox to add/update must be NOT NULL.")
    require(!boxIdsRemoveList.contains(null), "BoxId to remove must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    removeList.addAll(boxIdsRemoveList.map(Utils.calculateKey(_)).asJavaCollection)

    for (wb <- walletBoxUpdateList)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](Utils.calculateKey(wb.box.id()),
        new ByteArrayWrapper(_walletBoxSerializer.toBytes(wb))))

    storage.update(version,
      updateList,
      removeList)

    for (key <- removeList.asScala) {
      val btr = _walletBoxes.remove(key)
      removeWalletBoxByType(key)
      if (btr.isDefined)
        updateBoxesBalance(null, btr.get)
    }

    for (wba <- walletBoxUpdateList) {
      val key = Utils.calculateKey(wba.box.id())
      val bta = _walletBoxes.put(key, wba)
      addWalletBoxByType(wba)
      if (bta.isEmpty)
        updateBoxesBalance(wba, null)
    }

    this
  }

  override def lastVersionId : Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
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

  def isEmpty: Boolean = storage.isEmpty

  def getIterator: StorageIterator = storage.getIterator

}
