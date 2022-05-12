package com.horizen.forge

import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.util.serialization.{Reader, Writer}

case class ForgerList(forgerIndexes: Array[Int]) extends BytesSerializable {
  override type M = ForgerList

  def updateIndexes(indexToUpdate: Array[Int]): ForgerList = {
    indexToUpdate.foreach(toUpdate => {
      if (toUpdate < forgerIndexes.length) {
        forgerIndexes(toUpdate) = 1
      } else {
        throw new IndexOutOfBoundsException("Forger index to update is out of bound!")
      }
    })
    ForgerList(forgerIndexes)
  }

  override def serializer: ScorexSerializer[ForgerList] = ForgerListSerializer
}

object ForgerListSerializer extends ScorexSerializer[ForgerList] {
  override def serialize(obj: ForgerList, w: Writer): Unit = {
    w.putInt(obj.forgerIndexes.size)
    obj.forgerIndexes.foreach(index => {
      w.putInt(index)
    })
  }

  override def parse(r: Reader): ForgerList = {
    val nElement = r.getInt()
    val indexes: Array[Int] = new Array[Int](nElement)
    for (i <- 0 until nElement) {
      indexes(i) = r.getInt()
    }
    ForgerList(indexes)
  }
}
