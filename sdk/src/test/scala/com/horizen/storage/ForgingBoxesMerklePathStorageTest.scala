package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.consensus.ConsensusEpochNumber
import com.horizen.fixtures.{BoxFixture, IODBStoreFixture}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ForgerBoxMerklePathInfo, MerklePath, Pair}
import org.junit.{Before, Test}
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.mockito.{ArgumentMatchers, Mockito}
import java.util.{ArrayList => JArrayList, Optional => JOptional}

import scala.collection.JavaConverters._


class ForgingBoxesMerklePathStorageTest extends JUnitSuite
  with IODBStoreFixture
  with MockitoSugar
  with SidechainTypes
  with BoxFixture {

  @Test
  def updateVersion(): Unit = {
    val mockedStorage: Storage = mock[IODBStoreAdapter]
    val forgingBoxesMerklePathStorage = new ForgingBoxesMerklePathStorage(mockedStorage)

    val version = getVersion

    Mockito.when(mockedStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
      val actualVersion = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
      val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
      val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]

      assertEquals("Store update(...) actual Version is wrong.", version, actualVersion)
      assertTrue("Store update(...) actual list to update expected to be empty.", actualToUpdate.isEmpty)
      assertTrue("Store update(...) actual list to remove expected to be empty.", actualToRemove.isEmpty)
      })
      // For Test 2:
      .thenAnswer(answer => {
        throw new IllegalArgumentException("exception")
      })


    // Test 1: successful update of physical Store
    assertTrue("UpdateVersion expected to be successful.", forgingBoxesMerklePathStorage.updateVersion(version).isSuccess)


    // Test 2: failed to update of physical Store
    assertTrue("UpdateVersion expected to fail.", forgingBoxesMerklePathStorage.updateVersion(version).isFailure)
  }

  @Test
  def update(): Unit = {
    val mockedStorage: Storage = mock[IODBStoreAdapter]
    val forgingBoxesMerklePathStorage = new ForgingBoxesMerklePathStorage(mockedStorage)

    // Prepare data to update.
    val epochNumber = ConsensusEpochNumber @@ 100
    val boxMerklePathInfoSeq = Seq(
      ForgerBoxMerklePathInfo(
        getForgerBox,
        new MerklePath(new JArrayList())
      )
    )
    val version = getVersion

    Mockito.when(mockedStorage.lastVersionID()).thenReturn(JOptional.ofNullable[ByteArrayWrapper](null))

    Mockito.when(mockedStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
      val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
      val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]

      assertEquals("Store update(...) actual list to update size expected to be different.", 1, actualToUpdate.size())
      assertEquals("Different toUpdate epoch key expected.", forgingBoxesMerklePathStorage.epochKey(epochNumber), actualToUpdate.get(0).getKey)
      assertEquals("Different toUpdate value expected.",
        new ByteArrayWrapper(forgingBoxesMerklePathStorage.forgerBoxMerklePathInfoListSerializer.toBytes(boxMerklePathInfoSeq.asJava)),
        actualToUpdate.get(0).getValue)

      assertEquals("Store update(...) actual list to remove size expected to be different.", 1, actualToRemove.size())
      assertEquals("Different toRemove epoch key expected.",
        forgingBoxesMerklePathStorage.epochKey(ConsensusEpochNumber @@ (epochNumber - forgingBoxesMerklePathStorage.maxNumberOfStoredEpochs)),
        actualToRemove.get(0))

    })
      // For Test 2:
      .thenAnswer(answer => {
      throw new IllegalArgumentException("exception")
    })


    // Test 1: successful update of physical Store
    assertTrue("Update expected to be successful.", forgingBoxesMerklePathStorage.update(epochNumber, boxMerklePathInfoSeq).isSuccess)


    // Test 2: failed to update of physical Store
    assertTrue("Update expected to fail.", forgingBoxesMerklePathStorage.update(epochNumber, boxMerklePathInfoSeq).isFailure)
  }
}
