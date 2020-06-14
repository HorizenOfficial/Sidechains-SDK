package com.horizen.chain

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


trait LinkedElement[T] {
  def getParentId: T
}

class ElementsChain[ID, DATA <: LinkedElement[ID]](private var lastId: Option[ID] = None,
                                                   private var data: ArrayBuffer[DATA] = ArrayBuffer[DATA](),
                                                   private var idToHeightMap: mutable.HashMap[ID, Int] = mutable.HashMap[ID, Int]()) {
  def height: Int = data.size

  def bestData: Option[DATA] = data.lastOption

  def bestId: Option[ID] = lastId

  def contains(id: ID): Boolean = idToHeightMap.contains(id)

  def parentOf(id: ID): Option[ID] = dataById(id).map(_.getParentId)

  def heightById(id: ID): Option[Int] = idToHeightMap.get(id)

  def dataByHeight(requestedHeight: Int): Option[DATA] = data.lift(requestedHeight - 1)

  def dataById(id: ID): Option[DATA] = heightById(id).flatMap(dataByHeight)

  def idByHeight(requestedHeight: Int): Option[ID] = {
    if (requestedHeight == 0) {
      None // otherwise correct (sic!) id of parent of first chain data element is returned
    }
    else if (requestedHeight == height) {
      bestId
    }
    else {
      dataByHeight(requestedHeight + 1).map(_.getParentId)
    }
  }

  def setNewBestBlock(newId: ID, newData: DATA): Unit = {
    cutToId(newData.getParentId)
    appendData(newId, newData)
  }

  def cutToId(newBestId: ID): Unit = {
    if (height > 0) {
       if (!newBestId.equals(bestId.get)) {
        // we get an id, that is a part of another chain
        val newHeight = heightById(newBestId).getOrElse(throw new IllegalArgumentException("Parent id is not a part of chain. Failed to reorganize chain."))
        cutToSize(newHeight)
        lastId = Some(newBestId)
      }
    }
  }

  def appendData(newId: ID, newData: DATA): Unit = {
    if(height > 0 && !bestId.contains(newData.getParentId)) {
      throw new IllegalArgumentException("Try to append block with incorrect parent")
    }

    if (newData.getParentId == newId) {
      throw new IllegalArgumentException("Try to add incorrect data: element has himself as a parent")
    }

    data.append(newData)
    idToHeightMap.put(newId, height)

    lastId = Some(newId)
  }

  def chainAfter(id: ID): Seq[ID] = {
    if (contains(id) && height > 0) {
      var res: Seq[ID] = Seq()

      var currentHeight = heightById(id).get
      while (currentHeight < height) {
        currentHeight += 1
        res = res :+ dataByHeight(currentHeight).get.getParentId
      }
      res :+ bestId.get
    }
    else {
      Seq()
    }
  }

  def clear(): Unit = {
    lastId = None
    data = ArrayBuffer[DATA]()
    idToHeightMap = mutable.HashMap[ID, Int]()
  }

  def getLastDataByPredicate(p: DATA => Boolean): Option[DATA] = getLastDataByPredicateTillHeight(height)(p)

  @tailrec
  final def getLastDataByPredicateTillHeight(height: Integer)(p: DATA => Boolean): Option[DATA] = {
    dataByHeight(height) match {
      case Some(dataForHeight) => if (p(dataForHeight)) Some(dataForHeight) else getLastDataByPredicateTillHeight(height - 1)(p)
      case None => None
    }
  }

  @tailrec
  private def getLastIdsForHeight(counter: Int, lastHeight: Int, collectedIds: Seq[ID]): Seq[ID] = {
    require(counter >= 0, "Counter shall be positive")

    if (counter == 0) {
      collectedIds
    }
    else {
      val newLastId = data(lastHeight - 1).getParentId
      getLastIdsForHeight(counter - 1, lastHeight - 1, collectedIds :+ newLastId)
    }
  }

  private def cutToSize(newSize: Int): Unit = {
    require(newSize < height, "Chain after cut shall be shorter than initial chain")
    val tailSize = data.size - newSize

    val idsToRemove = getLastIdsForHeight(tailSize - 1, height, Seq(bestId.get)).reverse
    idToHeightMap --= idsToRemove // remove cut ids from (id -> height) map

    val removedData = data.takeRight(tailSize)
    data.reduceToSize(newSize)

    require(idsToRemove.size == removedData.size, "idsToRemove.size != removedData.size")
  }
}