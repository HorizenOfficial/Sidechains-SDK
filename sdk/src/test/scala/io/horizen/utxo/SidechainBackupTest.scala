package io.horizen.utxo

import io.horizen.SidechainTypes
import io.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import io.horizen.fork.{ForkManagerUtil, Sc2ScFork, Sc2ScOptionalForkConfigurator}
import io.horizen.params.{MainNetParams, NetworkParams}
import io.horizen.proposition.PublicKey25519Proposition
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import io.horizen.utils.{ByteArrayWrapper, BytesUtils, Utils, Pair => JPair}
import io.horizen.utxo.backup.{BackupBox, BoxIterator}
import io.horizen.utxo.box.{BoxSerializer, CoinsBox}
import io.horizen.utxo.companion.SidechainBoxesCompanion
import io.horizen.utxo.customtypes.{CustomBox, CustomBoxSerializer}
import io.horizen.utxo.state.SidechainBackup
import io.horizen.utxo.storage.{BackupStorage, BoxBackupInterface}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.rules.TemporaryFolder
import org.junit.{Before, Rule, Test}
import org.scalatestplus.mockito.MockitoSugar.mock
import sparkz.crypto.hash.Blake2b256

import java.lang.{Byte => JByte}
import java.util.{ArrayList => JArrayList, HashMap => JHashMap}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class SidechainBackupTest
  extends StoreFixture
  with SecretFixture
  with TransactionFixture
  {

  val customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers, false)

  val boxListFirstModifier = new ListBuffer[SidechainTypes#SCB]()
  val boxListSecondModifier = new ListBuffer[SidechainTypes#SCB]()
  val storedBoxListFirstModifier = new ListBuffer[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
  val storedBoxListSecondModifier = new ListBuffer[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
  val firstModifierBoxLength = 7

  val firstModifier: ByteArrayWrapper = getVersion
  val secondModifier: ByteArrayWrapper = getVersion

  val params: NetworkParams = MainNetParams()

  val sc2ScForkMock: Sc2ScFork = mock[Sc2ScFork]

  val backupper: BoxBackupInterface = new BoxBackupInterface {
    override def backup(source: BoxIterator, db: BackupStorage): Unit = {
      val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

      var optionalBox = source.nextBox(true)
      while(optionalBox.isPresent) {
        val box = optionalBox.get.getBox
        if (!box.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]]) {
          updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](Utils.calculateKey(box.id()),
            new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(box))))
        }
        optionalBox = source.nextBox(true)
      }
      if (updateList.size() != 0)
        db.update(getVersion,updateList)
    }
  }

  val _temporaryFolder = new TemporaryFolder()
  @Rule  def temporaryFolder = _temporaryFolder

  @Before
  def setup(): Unit = {
    boxListFirstModifier ++= getZenBoxList(5).asScala.toList
    boxListFirstModifier ++= getCustomBoxList(firstModifierBoxLength).asScala.map(_.asInstanceOf[SidechainTypes#SCB])

    boxListSecondModifier ++= getZenBoxList(5).asScala.toList
    boxListSecondModifier ++= getCustomBoxList(5).asScala.map(_.asInstanceOf[SidechainTypes#SCB])

    for (b <- boxListFirstModifier) {
      storedBoxListFirstModifier.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(b.id()))
        val value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))
        new JPair(key, value)
      })
    }

    for (b <- boxListSecondModifier) {
      storedBoxListSecondModifier.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(b.id()))
        val value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))
        new JPair(key, value)
      })
    }

    ForkManagerUtil.initializeForkManager(new Sc2ScOptionalForkConfigurator, "regtest")
  }

  @Test
  def testCreateBackupWithNoCopy(): Unit = {
    //Create temporary SidechainStateStorage
    val stateStorageFile = temporaryFolder.newFolder("sidechainStateStorage")
    var stateStorage = new VersionedLevelDbStorageAdapter(stateStorageFile)

    //Create temporary BackupStorage
    val backupStorageFile = temporaryFolder.newFolder("backupStorage")
    val backupStorage = new VersionedLevelDbStorageAdapter(backupStorageFile)

    //Update a first time the SidechainStateStorage with some custom and zen boxes
    stateStorage.update(firstModifier, storedBoxListFirstModifier.asJava, new JArrayList[ByteArrayWrapper]())
    //Update a second time the SidechainStateStorage with some custom and zen boxes
    stateStorage.update(secondModifier, storedBoxListSecondModifier.asJava, new JArrayList[ByteArrayWrapper]())
    stateStorage.close()

    //Instantiate a SidechainBackup class and call createBackup with no Copy option
    val sidechainBakcup = new SidechainBackup(customBoxSerializers = customBoxesSerializers, backUpStorage = backupStorage, backUpper = backupper, params = params, sc2ScForkMock)
    sidechainBakcup.createBackup(stateStorageFile.getPath, BytesUtils.toHexString(firstModifier.data()), false)

    //Read the backup storage created and verify that contains only firstModifierBoxLength elements. (We did a rollback to the first modifier)
    val storedBoxes = readStorage(new BoxIterator(backupStorage.getIterator(), sidechainBoxesCompanion))
    assertEquals("BackupStorage should contains only the firstModifierBoxLength CustomBoxes of the storedBoxListFirstModifier!",storedBoxes.size(), firstModifierBoxLength)

    //Verify that the backupped boxes are the ones saved in the first SidechainStateStorage update and they are not CoinBoxes.
    storedBoxes.forEach(box => {
      val storageElement = new JPair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper(box.getBoxKey), new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(box.getBox)))
      assertTrue("Restored boxes should be inside storedBoxListFirstModifier",storedBoxListFirstModifier.contains(storageElement))
      assertTrue("Restored boxes shouldn't be CoinBoxes!",!box.getBox.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]])
    })

    stateStorage = new VersionedLevelDbStorageAdapter(stateStorageFile)
    //Verify that the lastVersion of the StateStorage now is the firstModifier
    assertEquals(stateStorage.lastVersionID().get().data().deep, firstModifier.data().deep)
  }

  @Test
  def testCreateBackupWithCopy(): Unit = {
    //Create temporary SidechainStateStorage
    val stateStorageFile = temporaryFolder.newFolder("sidechainStateStorage")
    var stateStorage = new VersionedLevelDbStorageAdapter(stateStorageFile)

    //Create temporary BackupStorage
    val backupStorageFile = temporaryFolder.newFolder("backupStorage")
    val backupStorage = new VersionedLevelDbStorageAdapter(backupStorageFile)

    //Update a first time the SidechainStateStorage with some custom and zen boxes
    val firstModifier = getVersion
    stateStorage.update(firstModifier, storedBoxListFirstModifier.asJava, new JArrayList[ByteArrayWrapper]())
    //Update a second time the SidechainStateStorage with some custom and zen boxes
    val secondModifier = getVersion
    stateStorage.update(secondModifier, storedBoxListSecondModifier.asJava, new JArrayList[ByteArrayWrapper]())
    stateStorage.close()

    //Instantiate a SidechainBackup class and call createBackup
    val sidechainBakcup = new SidechainBackup(customBoxSerializers = customBoxesSerializers, backUpStorage = backupStorage, backUpper = backupper, params = params, sc2ScForkMock)
    sidechainBakcup.createBackup(stateStorageFile.getPath, BytesUtils.toHexString(firstModifier.data()), true)

    //Read the backup storage created and verify that contains only firstModifierBoxLength elements. (We did a rollback to the first modifier)
    val storedBoxes = readStorage(new BoxIterator(backupStorage.getIterator(), sidechainBoxesCompanion))
    assertEquals("BackupStorage should contains only the firstModifierBoxLength CustomBoxes of the storedBoxListFirstModifier!",storedBoxes.size(), firstModifierBoxLength)

    //Verify that the backupped boxes are the ones saved in the first SidechainStateStorage update and they are not CoinBoxes.
    storedBoxes.forEach(box => {
      val storageElement = new JPair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper(box.getBoxKey), new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(box.getBox)))
      assertTrue("Restored boxes should be inside storedBoxListFirstModifier",storedBoxListFirstModifier.contains(storageElement))
      assertTrue("Restored boxes shouldn't be CoinBoxes!",!box.getBox.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]])
    })

    stateStorage = new VersionedLevelDbStorageAdapter(stateStorageFile)
    //Verify that the lastVersion of the StateStorage now is the firstModifier
    assertEquals(stateStorage.lastVersionID().get().data().deep, secondModifier.data().deep)
  }

  def readStorage(sidechainStateStorageBoxIterator: BoxIterator): JArrayList[BackupBox] = {
    val storedBoxes = new JArrayList[BackupBox]()

    var optionalBox = sidechainStateStorageBoxIterator.nextBox
    while(optionalBox.isPresent) {
      storedBoxes.add(optionalBox.get)
      optionalBox = sidechainStateStorageBoxIterator.nextBox
    }
    storedBoxes
  }

}
