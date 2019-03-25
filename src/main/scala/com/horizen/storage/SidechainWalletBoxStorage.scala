package com.horizen.storage

import java.lang.{Exception => JException}
import java.util.{Optional, ArrayList => JArrayList}

import javafx.util.{Pair => JPair}
import com.horizen.utils.ByteArrayWrapper
import com.horizen.{WalletBox, WalletBoxSerializer}
import scorex.util.ScorexLogging

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Try}
import scala.collection.JavaConverters._

class SidechainWalletBoxStorage (storage : Storage)
  extends ScorexLogging
{
  // Version - block Id
  // Key - byte array box Id
  // No remove operation

  def get (walletBoxId : ByteArrayWrapper) : Try[WalletBox] = {
    val walletBoxBytes = storage.get(walletBoxId)

    if (walletBoxBytes.isPresent)
      WalletBoxSerializer.serializer.parseBytes(walletBoxBytes.get().data)
    else
      Failure(new JException("Secret key not found!"))
  }

  def get (walletBoxIds : List[ByteArrayWrapper]) : List[Try[WalletBox]] = {
    val walletBoxList = new ListBuffer[Try[WalletBox]]()

    for (p <- walletBoxIds)
      walletBoxList.append(get(p))

    walletBoxList.toList
  }

  def getAll : List[Try[WalletBox]] = {
    val secretList = ListBuffer[Try[WalletBox]]()
    val v = storage.getAll

    for(s <- storage.getAll.asScala)
      secretList.append(WalletBoxSerializer.serializer.parseBytes(s.getValue.data))

    secretList.toList
  }

  def update (version : ByteArrayWrapper, walletBoxToUpdate : WalletBox, boxIdToRemove : ByteArrayWrapper) : Unit = {
    val keyToUpdate = new ByteArrayWrapper(walletBoxToUpdate.box.id())
    val valueToUpdate = new ByteArrayWrapper(walletBoxToUpdate.serializer.toBytes(walletBoxToUpdate))

    storage.update(version,
      List(boxIdToRemove).asJava,
      List(new JPair(keyToUpdate, valueToUpdate)).asJava)
  }

  def update (version : ByteArrayWrapper, walletBoxUpdateList : List[WalletBox], boxIdsRemoveList : List[ByteArrayWrapper]) : Unit = {
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    for (b <- walletBoxUpdateList)
      updateList.add(new JPair[ByteArrayWrapper,ByteArrayWrapper](new ByteArrayWrapper(b.box.id()),
        new ByteArrayWrapper(b.serializer.toBytes(b))))

    storage.update(version,
      boxIdsRemoveList.asJava,
      updateList)
  }

  def lastVesrionId : Optional[ByteArrayWrapper] = {
    storage.lastVersionID()
  }

  def rollbackVersions : List[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Unit = {
    storage.rollback(version)
  }

}
