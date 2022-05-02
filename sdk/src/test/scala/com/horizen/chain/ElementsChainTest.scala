package com.horizen.chain

import org.junit.Assert.{assertEquals, assertFalse, assertNotEquals, assertTrue}
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import scala.util.Try

case class testData (parent: Integer, id: Integer) extends LinkedElement[Integer] {
  override def getParentId: Integer = parent
}

object dataGenerator {
  var lastGeneratedId: Integer = 0
  var parentId: Integer = 0

  def reset(): dataGenerator.type = {
    lastGeneratedId = 0
    parentId = 0
    this
  }

  def getNextData: (Integer, testData) = {
    lastGeneratedId += 1
    val data = (lastGeneratedId, testData(parentId, lastGeneratedId))
    parentId = lastGeneratedId

    data
  }

  def setParentId(newParentId: Integer): dataGenerator.type = {
    parentId = newParentId
    this
  }
}

class ElementsChainTest extends JUnitSuite {

  private def checkStorageElementIsPresent[ID, DATA <: LinkedElement[ID]](chainStorage: ElementsChain[ID, DATA], id: ID, data: DATA, height: Integer): Unit = {
    assertEquals("ChainStorage expected to find id ", true, chainStorage.contains(id))
    assertEquals("ChainStorage expected to find id of existent height", id, chainStorage.idByHeight(height).get)
    assertEquals("ChainStorage expected to find data of existent height", data, chainStorage.dataByHeight(height).get)
    assertEquals("ChainStorage expected to find height of existent id", height, chainStorage.heightById(id).get)
    assertEquals("ChainStorage expected to find data of existent id", data, chainStorage.dataById(id).get)
    assertEquals("Chain storage expected to find parent for element", data.getParentId, chainStorage.parentOf(id).get)
  }

  private def checkStorageElementIsNotPresent[ID, DATA <: LinkedElement[ID]](chainStorage: ElementsChain[ID, DATA], id: ID, data: DATA, height: Integer): Unit = {
    assertNotEquals("ChainStorage expected to not find id ", true, chainStorage.contains(id))
    assertNotEquals("ChainStorage expected to not find id of non existent height", id, chainStorage.idByHeight(height).getOrElse(-1))
    assertNotEquals("ChainStorage expected to not find data of non existent height", data, chainStorage.dataByHeight(height).getOrElse(-1))
    assertNotEquals("ChainStorage expected to not find height of non existent id", height, chainStorage.heightById(id).getOrElse(-1))
    assertNotEquals("ChainStorage expected to not find data of non existent id", data, chainStorage.dataById(id).getOrElse(-1))
    assertEquals("Chain storage expected not to find parent non for missing element", None, chainStorage.parentOf(id))
  }

  private def checkBestElementIs[ID, DATA <: LinkedElement[ID]](chainStorage: ElementsChain[ID, DATA], bestId: ID, bestData: DATA, bestHeight: Integer): Unit = {
    assertEquals(s"ChainStorage expected to have height ${bestHeight}", bestHeight, chainStorage.height)
    assertEquals("ChainStorage expected to have valid best id", bestId, chainStorage.bestId.get)
    assertEquals("ChainStorage expected to have valid best data", bestData, chainStorage.bestData.get)
  }

  private def checkBestElementIsNot[ID, DATA <: LinkedElement[ID]](chainStorage: ElementsChain[ID, DATA], bestId: ID, bestData: DATA): Unit = {
    assertNotEquals("ChainStorage expected to not have valid best id", bestId, chainStorage.bestId.get)
    assertNotEquals("ChainStorage expected to not have valid best data", bestData, chainStorage.bestData.get)
  }

  private def checkAddNewBestBlockIsSuccessfully[ID, DATA <: LinkedElement[ID]](chainStorage: ElementsChain[ID, DATA], id: ID, data: DATA): Unit = {
    val res = Try {chainStorage.setNewBestBlock(id, data)}
    assertEquals("Adding new block had been failed", true, res.isSuccess)
  }

  private def checkAddNewBestBlockIsFailed[ID, DATA <: LinkedElement[ID]](chainStorage: ElementsChain[ID, DATA], id: ID, data: DATA): Unit = {
    val res = Try {chainStorage.setNewBestBlock(id, data)}
    assertEquals("Adding new block had been failed", true, res.failed.get.isInstanceOf[IllegalArgumentException])
  }

  private def checkChainIsEmpty[ID, DATA <: LinkedElement[ID]](chainStorage: ElementsChain[ID, DATA], id: ID, data: DATA): Unit = {
    assertEquals("Empty ChainStorage expected to have height 0", 0, chainStorage.height)
    assertTrue("Empty ChainStorage expected to have no tip", chainStorage.bestId.isEmpty)
    assertTrue("Empty ChainStorage expected to have no tipInfo", chainStorage.bestData.isEmpty)

    assertTrue("Empty ChainStorage expected not to find height of nonexistent modifier", chainStorage.heightById(id).isEmpty)
    assertFalse("Empty ChainStorage expected not to contain nonexistent modifier", chainStorage.contains(id))
    assertTrue("Empty ChainStorage expected not to find modifier for inconsistent height", chainStorage.dataByHeight(1).isEmpty)
    assertTrue("Empty ChainStorage expected not to find modifier id for inconsistent height", chainStorage.idByHeight(0).isEmpty)
    assertTrue("Empty ChainStorage expected not to find modifier id for inconsistent height", chainStorage.idByHeight(1).isEmpty)
    assertTrue("Empty ChainStorage expected to return empty chain from nonexistent modifier", chainStorage.chainAfter(id).isEmpty)
    assertEquals("No parent shall be found for empty chain", None, chainStorage.parentOf(id))
  }

  @Test
  def emptyChainStorage(): Unit = {
    val chainStorage = new ElementsChain[Integer, testData]()

    checkChainIsEmpty[Integer, testData](chainStorage, -1, testData(0, 0))
  }

  @Test
  def addUnconnectedData(): Unit = {
    val chainStorage = new ElementsChain[Integer, testData]()

    val (id1, data1) = dataGenerator.reset().getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id1, data1)

    val (unconnectedId, unconnectedData) = dataGenerator.setParentId(1000).getNextData
    val addingRes = Try {chainStorage.appendData(unconnectedId, unconnectedData)}
    assertTrue("Adding unconnected bock shall be failed", addingRes.failed.get.isInstanceOf[IllegalArgumentException])
  }

  @Test
  def addingAndRemovingData(): Unit = {
    val chainStorage = new ElementsChain[Integer, testData]()

    val (id1, data1) = dataGenerator.reset().getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id1, data1)
    checkStorageElementIsPresent(chainStorage, id1, data1, 1)
    checkBestElementIs(chainStorage, id1, data1, 1)
    assertEquals("Chain from shall be equal", Seq(id1), chainStorage.chainAfter(id1))
    assertEquals("Id shall be found for height 0", None, chainStorage.idByHeight(0))

    val (id2, data2) = dataGenerator.getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id2, data2)
    checkStorageElementIsPresent(chainStorage, id2, data2, 2)
    checkBestElementIs(chainStorage, id2, data2, 2)
    assertEquals("Chain from shall be equal", Seq(id1, id2), chainStorage.chainAfter(id1))

    val (id3, data3) = dataGenerator.getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id3, data3)
    assertEquals("Chain from shall be equal", Seq(id2, id3), chainStorage.chainAfter(id2))

    val (id4, data4) = dataGenerator.getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id4, data4)
    checkStorageElementIsPresent(chainStorage, id4, data4, 4)
    checkBestElementIs(chainStorage, id4, data4, 4)
    assertEquals("Chain from shall be equal", Seq(id4), chainStorage.chainAfter(id4))

    val (newId4, newData4) = dataGenerator.setParentId(3).getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, newId4, newData4)
    checkStorageElementIsNotPresent(chainStorage, id4, data4, 4)
    checkBestElementIsNot(chainStorage, id4, data4)
    checkStorageElementIsPresent(chainStorage, newId4, newData4, 4)
    checkBestElementIs(chainStorage, newId4, newData4, 4)
    assertEquals("Chain from shall be equal", Seq(), chainStorage.chainAfter(id4))
    assertEquals("Searching by predicate had been failed", Some(data3), chainStorage.getLastDataByPredicate(data => data.getParentId == data3.getParentId))
    assertEquals("Searching by predicate had been failed", Some(data2), chainStorage.getLastDataByPredicateTillHeight(2)(data => data.getParentId == data2.getParentId))


    val (newId2, newData2) = dataGenerator.setParentId(1).getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, newId2, newData2)
    checkStorageElementIsNotPresent(chainStorage, id3, data3, 3)
    checkBestElementIsNot(chainStorage, newId4, newData4)
    checkStorageElementIsNotPresent(chainStorage, id2, data2, 2)
    checkStorageElementIsPresent(chainStorage, newId2, newData2, 2)
    checkBestElementIs(chainStorage, newId2, newData2, 2)
    assertEquals("Searching by predicate had been failed", None, chainStorage.getLastDataByPredicate(data => data.getParentId == data3.getParentId))

    val (unconnectedId, unconnectedData) = dataGenerator.setParentId(100).getNextData  // 100 is just some unconnected id to chain storage id
    checkAddNewBestBlockIsFailed(chainStorage, unconnectedId, unconnectedData)
    checkStorageElementIsNotPresent(chainStorage, unconnectedId, unconnectedData, 3)
    checkBestElementIsNot(chainStorage, unconnectedId, unconnectedData)
  }

  @Test
  def clearStorage(): Unit = {
    val chainStorage = new ElementsChain[Integer, testData]()

    val (id1, data1) = dataGenerator.reset().getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id1, data1)
    checkStorageElementIsPresent(chainStorage, id1, data1,1)

    val (id2, data2) = dataGenerator.getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id2, data2)
    checkStorageElementIsPresent(chainStorage, id2, data2,2)

    val (id3, data3) = dataGenerator.getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id3, data3)
    checkStorageElementIsPresent(chainStorage, id3, data3,3)

    chainStorage.clear()
    checkChainIsEmpty[Integer, testData](chainStorage, -1, testData(0, 0))
    checkStorageElementIsNotPresent(chainStorage, id1, data1,1)
    checkStorageElementIsNotPresent(chainStorage, id2, data2,2)
    checkStorageElementIsNotPresent(chainStorage, id3, data3,3)
  }

  @Test
  def clearStorageCutByZeroElementParent(): Unit = {
    val chainStorage = new ElementsChain[Integer, testData]()

    val (id1, data1) = dataGenerator.reset().getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id1, data1)
    checkStorageElementIsPresent(chainStorage, id1, data1,1)

    val (id2, data2) = dataGenerator.getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id2, data2)
    checkStorageElementIsPresent(chainStorage, id2, data2,2)

    val (id3, data3) = dataGenerator.getNextData
    checkAddNewBestBlockIsSuccessfully(chainStorage, id3, data3)
    checkStorageElementIsPresent(chainStorage, id3, data3,3)

    val res= Try{    chainStorage.cutToId(data1.getParentId)}
    assertTrue("Cut to parent of first element shall have no effect", res.isFailure)
  }

}