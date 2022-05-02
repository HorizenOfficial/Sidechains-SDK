package com.horizen.storage

import com.google.common.primitives.Ints
import com.horizen.SidechainTypes
import com.horizen.box.BoxSerializer
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import com.horizen.customtypes.{CustomBox, CustomBoxSerializer}
import com.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import com.horizen.utils.{BlockFeeInfo, BlockFeeInfoSerializer, ByteArrayWrapper, Pair, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import org.junit.Assert._
import org.junit._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.crypto.hash.Blake2b256

import java.lang.{Byte => JByte}
import java.util.{ArrayList => JArrayList, HashMap => JHashMap, Optional => JOptional}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

class SidechainStateStorageTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypes
{
  val mockedPhysicalStorage: Storage = mock[VersionedLevelDbStorageAdapter]

  val boxList = new ListBuffer[SidechainTypes#SCB]()
  val storedBoxList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  val customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers)

  val withdrawalEpochInfo = WithdrawalEpochInfo(1, 2)

  val consensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(1)

  @Before
  def setUp(): Unit = {

    boxList ++= getZenBoxList(5).asScala.toList
    boxList ++= getCustomBoxList(5).asScala.map(_.asInstanceOf[SidechainTypes#SCB])


    for (b <- boxList) {
      storedBoxList.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(b.id()))
        val value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))
        new Pair(key,value)
      })
    }

    Mockito.when(mockedPhysicalStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedBoxList.find(_.getKey.equals(answer.getArgument(0))) match {
          case Some(pair) => JOptional.of(pair.getValue)
          case None => JOptional.empty()
        }
      })
  }

  @Test
  def testUpdate(): Unit = {
    val stateStorage = new SidechainStateStorage(mockedPhysicalStorage, sidechainBoxesCompanion)
    var tryRes: Try[SidechainStateStorage] = null
    val expectedException = new IllegalArgumentException("on update exception")

    // Test1: get one item
    assertEquals("Storage must return existing Box.", boxList(3), stateStorage.getBox(boxList(3).id()).get)

    // Test 2: try get non-existing item
    assertEquals("Storage must NOT contain requested Box.", None, stateStorage.getBox("non-existing id".getBytes()))

    // Data for Test 1:
    val version = getVersion
    val toUpdate = new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    toUpdate.add(storedBoxList.head)

    // withdrawals info
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.withdrawalEpochInformationKey),
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfo))))

    // block fee info
    val nextBlockFeeInfoCounter: Int = 0
    val blockFeeInfo: BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("1234".getBytes()).publicImage())
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getBlockFeeInfoCounterKey(withdrawalEpochInfo.epoch)),
      new ByteArrayWrapper(Ints.toByteArray(nextBlockFeeInfoCounter))))
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getBlockFeeInfoKey(withdrawalEpochInfo.epoch, nextBlockFeeInfoCounter)),
      new ByteArrayWrapper(BlockFeeInfoSerializer.toBytes(blockFeeInfo))))

    // consensus epoch
    toUpdate.add(new Pair(stateStorage.consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(consensusEpoch))))
    val toRemove = java.util.Arrays.asList(storedBoxList(2).getKey)

    Mockito.when(mockedPhysicalStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
      val actualVersion = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
      val actualToUpdate = answer.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
      val actualToRemove = answer.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]
      assertEquals("StateStorage.update(...) actual Version is wrong.", version, actualVersion)
      assertEquals("StateStorage.update(...) actual toUpdate list is wrong.", toUpdate, actualToUpdate)
      assertEquals("StateStorage.update(...) actual toRemove list is wrong.", toRemove, actualToRemove)
    })
      // For Test 2:
      .thenAnswer(answer => throw expectedException)


    // Test 1: test successful update
    tryRes = stateStorage.update(version, withdrawalEpochInfo, Set(boxList.head),
      Set(new ByteArrayWrapper(boxList(2).id())), Seq(), consensusEpoch, None, blockFeeInfo, None, false)
    assertTrue("StateStorage successful update expected, instead exception occurred:\n %s".format(if(tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)


    // Test 2: test failed update, when Storage throws an exception
    val box = getZenBox
    tryRes = stateStorage.update(version, withdrawalEpochInfo, Set(box),
      Set(new ByteArrayWrapper(boxList(3).id())), Seq(), consensusEpoch, None, blockFeeInfo, None, false)
    assertTrue("StateStorage failure expected during update.", tryRes.isFailure)
    assertEquals("StateStorage different exception expected during update.", expectedException, tryRes.failed.get)
    assertTrue("Storage should NOT contain Box that was tried to update.", stateStorage.getBox(box.id()).isEmpty)
    assertTrue("Storage should contain Box that was tried to remove.", stateStorage.getBox(boxList(3).id()).isDefined)
    assertEquals("Storage should return existing Box.", boxList(3), stateStorage.getBox(boxList(3).id()).get)
  }

  @Test
  def testExceptions() : Unit = {
    var exceptionThrown = false

    try {
      val stateStorage = new SidechainStateStorage(null, sidechainBoxesCompanion)
    } catch {
      case e : IllegalArgumentException => exceptionThrown = true
    }

    assertTrue("SidechainStateStorage constructor. Exception must be thrown if storage is not specified.",
      exceptionThrown)

    exceptionThrown = false
    try {
      val stateStorage = new SidechainStateStorage(mockedPhysicalStorage, null)
    } catch {
      case e : IllegalArgumentException => exceptionThrown = true
    }

    assertTrue("SidechainStateStorage constructor. Exception must be thrown if boxesCompation is not specified.",
      exceptionThrown)

    val stateStorage = new SidechainStateStorage(mockedPhysicalStorage, sidechainBoxesCompanion)

    assertTrue("SidechainStorage.rollback. Method must return Failure if NULL version specified.",
      stateStorage.rollback(null).isFailure)
  }
}
