package com.horizen.storage.leveldb

import com.horizen.utils.Checker
import sparkz.core.serialization.SparkzSerializer
import sparkz.util.serialization.{Reader, Writer}

/**
  * That source code had been copied/modified from ErgoPlatform Project
  *
  * Describes a set of changes which could be reverted later.
  * @param insertedKeys - inserted keys
  * @param removed - removed entries (key, value)
  * @param altered - altered keys (key, oldValue)
  */
final case class ChangeSet(insertedKeys: Seq[Array[Byte]],
                           removed: Seq[(Array[Byte], Array[Byte])],
                           altered: Seq[(Array[Byte], Array[Byte])])

object ChangeSetSerializer extends SparkzSerializer[ChangeSet] {

  override def serialize(obj: ChangeSet, w: Writer): Unit = {
    w.putUInt(obj.insertedKeys.size)
    obj.insertedKeys.foreach { k =>
      require(k.length <= 255, "Illegal key size")
      w.putUByte(k.length)
      w.putBytes(k)
    }
    w.putUInt(x = obj.removed.size)
    obj.removed.foreach { case (k, v) =>
      require(k.length <= 255, "Illegal key size")
      w.putUByte(k.length)
      w.putBytes(k)
      w.putUInt(v.length)
      w.putBytes(v)
    }
    w.putUInt(obj.altered.size)
    obj.altered.foreach { case (k, oldV) =>
      require(k.length <= 255, "Illegal key size")
      w.putUByte(k.length)
      w.putBytes(k)
      w.putUInt(oldV.length)
      w.putBytes(oldV)
    }
  }

  override def parse(r: Reader): ChangeSet = {
    val insertedQty = r.getUInt().toInt
    val insertedKeys = (0 until insertedQty).foldLeft(Seq.empty[Array[Byte]]) { case (acc, _) =>
      val len = r.getUByte()
      acc :+ Checker.readBytes(r, len, "acc")
    }
    val removedQty = r.getUInt().toInt
    val removed = (0 until removedQty).foldLeft(Seq.empty[(Array[Byte], Array[Byte])]) { case (acc, _) =>
      val kLen = r.getUByte()
      val k = Checker.readBytes(r, kLen, "k")
      val vLen = r.getUInt().toInt
      val v = Checker.readBytes(r, vLen, "v")
      acc :+ (k -> v)
    }
    val alteredQty = r.getUInt().toInt
    val altered = (0 until alteredQty).foldLeft(Seq.empty[(Array[Byte], Array[Byte])]) { case (acc, _) =>
      val kLen = r.getUByte()
      val k = Checker.readBytes(r, kLen, "k")
      val oldVLen = r.getUInt().toInt
      val oldV = Checker.readBytes(r, oldVLen, "olv v")
      acc :+ (k -> oldV)
    }
    ChangeSet(insertedKeys, removed, altered)
  }

}
