package com.horizen.utxo.storage

import com.horizen.SidechainTypes
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.fixtures.{BoxFixture, StoreFixture}
import com.horizen.librustsidechains.FieldElement
import com.horizen.proposition.Proposition
import com.horizen.storage.Storage
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, Utils, UtxoMerkleTreeLeafInfo, Pair => JPair}
import com.horizen.utxo.box.{Box, ZenBox}
import org.junit.Assert._
import org.junit.Test
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar

import java.util
import java.util.{Optional, List => JList}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

class SidechainStateUtxoMerkleTreeStorageTest
  extends JUnitSuite
    with BoxFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypes {

  val positions: Seq[Int] = Seq(0, 1, 2, 4, 6, 7, 10, 50, 51, 100, 1000)
  val utxoLeafInfoSeq: Seq[(ZenBox, UtxoMerkleTreeLeafInfo)] = positions.map(pos => {
    val box = getZenBox(pos)
    val leafFE: FieldElement = CryptoLibProvider.cswCircuitFunctions.getUtxoMerkleTreeLeaf(box.asInstanceOf[Box[Proposition]])
    val info = UtxoMerkleTreeLeafInfo(leafFE.serializeFieldElement(), pos)
    leafFE.freeFieldElement()
    (box, info)
  })


  @Test
  def initEmptyStorage(): Unit = {
    // mock empty storage
    val mockedPhysicalStorage: Storage = mock[Storage]
    Mockito.when(mockedPhysicalStorage.getAll).thenReturn(util.Arrays.asList[JPair[ByteArrayWrapper,ByteArrayWrapper]]())
    Mockito.when(mockedPhysicalStorage.get(ArgumentMatchers.any[ByteArrayWrapper]())).thenReturn(Optional.empty[ByteArrayWrapper]())

    // Init
    val utxoStorage = new SidechainStateUtxoMerkleTreeStorage(mockedPhysicalStorage)

    // Regression: check empty storage merkle tree root value
    val expectedRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)
    assertEquals("Utxo merkle root is different.",
      "233d9610a4a95042a6ce17822c7fd6ec217fd075f8e55bd492446b2006711d0a", expectedRoot)
  }

  @Test
  def initNonEmptyStorage(): Unit = {
    // mock the storage with records
    val mockedPhysicalStorage: Storage = mock[Storage]
    Mockito.when(mockedPhysicalStorage.getAll).thenReturn({
      utxoLeafInfoSeq.map {
        case (box, info) => new JPair(new ByteArrayWrapper(box.id()), new ByteArrayWrapper(info.bytes))
      }.asJava
    })

    // Init
    val utxoStorage = new SidechainStateUtxoMerkleTreeStorage(mockedPhysicalStorage)

    // Regression: check non-empty storage merkle tree root value
    val expectedRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)
    assertEquals("Utxo merkle root is different.",
      "9034ef6733f47e6fca7905cb722a0bad35ec2c9d4467c6bb3d68b637c5ceed3e", expectedRoot)

    // Test getLeafInfo
    Mockito.when(mockedPhysicalStorage.get(ArgumentMatchers.any[ByteArrayWrapper]())).thenAnswer(answer => {
      val key: ByteArrayWrapper = answer.getArgument(0)
      utxoLeafInfoSeq
        .find(entry => key.equals(Utils.calculateKey(entry._1.id())))
        .map(entry => new ByteArrayWrapper(entry._2.bytes))
        .asJava
    })

    // Known leaves
    utxoLeafInfoSeq.foreach{
      case (box, info) =>
        val leafOpt = utxoStorage.getLeafInfo(box.id)
        assertTrue("Leaf expected to be found.", leafOpt.isDefined)
        assertEquals("Leaf is different.", info, leafOpt.get)
    }

    // Unknown leaf
    val randomBoxId = getRandomBoxId(12345L)
    assertTrue("Leaf expected to be missed.", utxoStorage.getLeafInfo(randomBoxId).isEmpty)
  }

  @Test
  def emptyStorageUpdate(): Unit = {
    // mock empty storage
    val mockedPhysicalStorage: Storage = mock[Storage]
    Mockito.when(mockedPhysicalStorage.getAll).thenReturn(util.Arrays.asList[JPair[ByteArrayWrapper,ByteArrayWrapper]]())
    Mockito.when(mockedPhysicalStorage.get(ArgumentMatchers.any[ByteArrayWrapper]())).thenReturn(Optional.empty[ByteArrayWrapper]())

    // Init
    val utxoStorage = new SidechainStateUtxoMerkleTreeStorage(mockedPhysicalStorage)

    // Update empty storage
    val version: ByteArrayWrapper = getVersion
    val boxesToAppend: Seq[SidechainTypes#SCB] = Seq(
      getZenBox(10L),
      getZenBox(20L),
      getZenBox(50L)
    )
    val boxesToRemove: Set[ByteArrayWrapper] = Set(
      getRandomBoxId(1234L),
      getRandomBoxId(5678L)
    )

    Mockito.when(mockedPhysicalStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[JList[JPair[ByteArrayWrapper, ByteArrayWrapper]]](),
      ArgumentMatchers.any[JList[ByteArrayWrapper]]()))
      .thenAnswer( data => {
        val actVersion: ByteArrayWrapper = data.getArgument(0)
        val actToUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = data.getArgument(1)
        val actToRemove: JList[ByteArrayWrapper] = data.getArgument(2)

        val expectedToUpdate = boxesToAppend.zipWithIndex.map {
          case (box, idx) =>
            val leafFE = utxoStorage.calculateLeaf(box)
            val leafInfo = UtxoMerkleTreeLeafInfo(leafFE.serializeFieldElement(), idx)
            leafFE.freeFieldElement()
            new JPair(new ByteArrayWrapper(Utils.calculateKey(box.id())), new ByteArrayWrapper(leafInfo.bytes))
        }.asJava

        val expectedToRemove = boxesToRemove.toSeq.map(id => Utils.calculateKey(id.data)).asJava

        assertEquals("Version is different.", version, actVersion)
        assertEquals("Update list is different.", expectedToUpdate, actToUpdate)
        assertEquals("Remove list is different.", expectedToRemove, actToRemove)
      })

    assertTrue("Storage expected to be updated", utxoStorage.update(version, boxesToAppend, boxesToRemove).isSuccess)


    // Regression: check merkle tree root
    val expectedRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)
    assertEquals("Utxo merkle root is different.",
      "4d837fae4732104bea1a0c9a4d56ea8d57ead12f2192ff01d96b890fcea3942a", expectedRoot)
  }

  @Test
  def storageUpdate(): Unit = {
    // mock the storage with records
    val mockedPhysicalStorage: Storage = mock[Storage]
    Mockito.when(mockedPhysicalStorage.getAll).thenReturn({
      utxoLeafInfoSeq.map {
        case (box, info) => new JPair(new ByteArrayWrapper(box.id()), new ByteArrayWrapper(info.bytes))
      }.asJava
    })

    // Init
    val utxoStorage = new SidechainStateUtxoMerkleTreeStorage(mockedPhysicalStorage)
    val initialRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)

    Mockito.when(mockedPhysicalStorage.get(ArgumentMatchers.any[ByteArrayWrapper]())).thenAnswer(answer => {
      val key: ByteArrayWrapper = answer.getArgument(0)
      utxoLeafInfoSeq
        .find(entry => key.equals(Utils.calculateKey(entry._1.id())))
        .map(entry => new ByteArrayWrapper(entry._2.bytes))
        .asJava
    })

    // Update non-empty storage
    val version: ByteArrayWrapper = getVersion
    val boxesToAppend: Seq[SidechainTypes#SCB] = Seq(
      getZenBox(10L),
      getZenBox(20L),
      getZenBox(50L)
    )

    // Remove first 2 entries (positions 0 and 1)
    val leavesToRemove = 2
    val boxesToRemove: Set[ByteArrayWrapper] = utxoLeafInfoSeq.take(leavesToRemove).map(entry => new ByteArrayWrapper(entry._1.id())).toSet

    val leafPositionsToAppend: Seq[Int] = (
      positions.take(leavesToRemove) ++ (0 to 1000).filter(pos => !positions.contains(pos))
      ).take(boxesToAppend.size)

    Mockito.when(mockedPhysicalStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[JList[JPair[ByteArrayWrapper, ByteArrayWrapper]]](),
      ArgumentMatchers.any[JList[ByteArrayWrapper]]()))
      // Test 1: normal update
      .thenAnswer( data => {
        val actVersion: ByteArrayWrapper = data.getArgument(0)
        val actToUpdate: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = data.getArgument(1)
        val actToRemove: JList[ByteArrayWrapper] = data.getArgument(2)

        val expectedToUpdate = boxesToAppend.zip(leafPositionsToAppend).map {
          case (box, pos) =>
            val leafFE = utxoStorage.calculateLeaf(box)
            val leafInfo = UtxoMerkleTreeLeafInfo(leafFE.serializeFieldElement(), pos)
            leafFE.freeFieldElement()
            new JPair(new ByteArrayWrapper(Utils.calculateKey(box.id())), new ByteArrayWrapper(leafInfo.bytes))
        }.asJava

        val expectedToRemove = boxesToRemove.toSeq.map(id => Utils.calculateKey(id.data)).asJava

        assertEquals("Version is different.", version, actVersion)
        assertEquals("Update list is different.", expectedToUpdate, actToUpdate)
        assertEquals("Remove list is different.", expectedToRemove, actToRemove)
      })
      // Test 2 storage internal exception
      .thenAnswer( _ => {
        throw new IllegalArgumentException("INTERNAL ERROR")
      })

    // Test 1: Successful update of non-empty storage.
    assertTrue("Storage expected to be updated", utxoStorage.update(version, boxesToAppend, boxesToRemove).isSuccess)

    // Regression: check merkle tree root
    val actualRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)
    assertEquals("Storage utxo merkle root is different.",
      "d269505eb471a3f70447392e6e66f02b1fb6fa7dcf1f057a7a0103ce084d1032", actualRoot)


    // Test 2: Emulate storage update exception and check that in memory merkle tree was restored.
    val version2 = getVersion
    val boxesToAppend2: Seq[SidechainTypes#SCB] = Seq(
      getZenBox(60L),
      getZenBox(70L)
    )
    val boxesToRemove2: Set[ByteArrayWrapper] = utxoLeafInfoSeq.takeRight(1).map(entry => new ByteArrayWrapper(entry._1.id())).toSet

    assertTrue("Storage update should fail.", utxoStorage.update(version2, boxesToAppend2, boxesToRemove2).isFailure)

    // Check merkle root to rollback to the same value as it was before during the initialization.
    // Note: during the test 1 we verified but haven't applied any changes to the physical storage
    val newRoot: String = BytesUtils.toHexString(utxoStorage.getMerkleTreeRoot)
    assertEquals("Empty storage utxo merkle root is different.", initialRoot, newRoot)
  }
}
