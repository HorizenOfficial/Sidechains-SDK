package io.horizen.utxo.storage

import com.google.common.primitives.Ints
import io.horizen.SidechainTypes
import io.horizen.block.{MainchainHeaderHash, WithdrawalEpochCertificate, WithdrawalEpochCertificateFixture, WithdrawalEpochCertificateSerializer}
import io.horizen.utxo.companion.SidechainBoxesCompanion
import io.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import io.horizen.params.{MainNetParams, NetworkParams}
import io.horizen.proposition.PublicKey25519Proposition
import io.horizen.sc2sc.{CrossChainMessage, CrossChainMessageSerializer}
import io.horizen.storage.Storage
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import io.horizen.utils.{ByteArrayWrapper, ListSerializer, Pair, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import io.horizen.utxo.backup.{BackupBox, BoxIterator}
import io.horizen.utxo.box.{BoxSerializer, CoinsBox}
import io.horizen.utxo.customtypes.{CustomBox, CustomBoxSerializer}
import io.horizen.utxo.fixtures.BoxFixture
import io.horizen.utxo.state.SidechainState
import io.horizen.utxo.utils.{BlockFeeInfo, BlockFeeInfoSerializer}
import org.junit.Assert._
import org.junit._
import org.junit.rules.TemporaryFolder
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.crypto.hash.Blake2b256

import java.lang.{Byte => JByte}
import java.nio.charset.StandardCharsets
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
    with WithdrawalEpochCertificateFixture
    with BoxFixture
{
  val mockedPhysicalStorage: Storage = mock[VersionedLevelDbStorageAdapter]
  val crossChainMessagesSerializer = new ListSerializer[CrossChainMessage](CrossChainMessageSerializer.getSerializer)
  val boxList = new ListBuffer[SidechainTypes#SCB]()
  val storedBoxList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
  val customStoredBoxList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  val customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers, false)

  val withdrawalEpochInfo = WithdrawalEpochInfo(1, 2)

  val params: NetworkParams = MainNetParams()

  val consensusEpoch: ConsensusEpochNumber = intToConsensusEpochNumber(1)

  val _temporaryFolder = new TemporaryFolder()

  val nonCeasingParams: NetworkParams = MainNetParams(isNonCeasing = true, sc2ScProvingKeyFilePath = Some("somePath"))

  val crossChainMessages : Seq[CrossChainMessage] = Seq(
    SidechainState.buildCrosschainMessageFromUTXO(getRandomCrossMessageBox(System.currentTimeMillis()), nonCeasingParams)
  )

  @Rule  def temporaryFolder = _temporaryFolder

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
      if (!b.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]]) {
        customStoredBoxList.append({
          val key = new ByteArrayWrapper(Blake2b256.hash(b.id()))
          val value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))
          new Pair(key,value)
        })
      }
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
    val stateStorage = new SidechainStateStorage(mockedPhysicalStorage, sidechainBoxesCompanion, params)
    var tryRes: Try[SidechainStateStorage] = null
    val expectedException = new IllegalArgumentException("on update exception")

    // Test1: get one item
    assertEquals("Storage must return existing Box.", boxList(3), stateStorage.getBox(boxList(3).id()).get)

    // Test 2: try get non-existing item
    assertEquals("Storage must NOT contain requested Box.", None, stateStorage.getBox("non-existing id".getBytes(StandardCharsets.UTF_8)))

    // Data for Test 1:
    val version = getVersion
    val toUpdate = new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    toUpdate.add(storedBoxList.head)

    // withdrawals info
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.withdrawalEpochInformationKey),
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfo))))

    // block fee info
    val nextBlockFeeInfoCounter: Int = 0
    val blockFeeInfo: BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("1234".getBytes(StandardCharsets.UTF_8)).publicImage())
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getBlockFeeInfoCounterKey(withdrawalEpochInfo.epoch)),
      new ByteArrayWrapper(Ints.toByteArray(nextBlockFeeInfoCounter))))
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getBlockFeeInfoKey(withdrawalEpochInfo.epoch, nextBlockFeeInfoCounter)),
      new ByteArrayWrapper(BlockFeeInfoSerializer.toBytes(blockFeeInfo))))

    // consensus epoch
    toUpdate.add(new Pair(stateStorage.consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(consensusEpoch))))
    val toRemove = java.util.Arrays.asList(storedBoxList(2).getKey)

    //forger list indexes
    toUpdate.add(new Pair(stateStorage.forgerListIndexKey, new ByteArrayWrapper(Array[Byte](0.toByte))))

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
      Set(new ByteArrayWrapper(boxList(2).id())), Seq(), Seq(), Seq(), Seq(), consensusEpoch,  Seq(), blockFeeInfo, None, false, new Array[Int](0), 0)
    assertTrue("StateStorage successful update expected, instead exception occurred:\n %s".format(if(tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)


    // Test 2: test failed update, when Storage throws an exception
    val box = getZenBox
    tryRes = stateStorage.update(version, withdrawalEpochInfo, Set(box), Set(new ByteArrayWrapper(boxList(3).id())),
      Seq(),  Seq(), Seq(), Seq(), consensusEpoch,  Seq(), blockFeeInfo, None, false, new Array[Int](0), 0)
    assertTrue("StateStorage failure expected during update.", tryRes.isFailure)
    assertEquals("StateStorage different exception expected during update.", expectedException, tryRes.failed.get)
    assertTrue("Storage should NOT contain Box that was tried to update.", stateStorage.getBox(box.id()).isEmpty)
    assertTrue("Storage should contain Box that was tried to remove.", stateStorage.getBox(boxList(3).id()).isDefined)
    assertEquals("Storage should return existing Box.", boxList(3), stateStorage.getBox(boxList(3).id()).get)
  }

  @Test
  def testUpdateNonCeasing(): Unit = {
    val stateStorage = new SidechainStateStorage(mockedPhysicalStorage, sidechainBoxesCompanion, nonCeasingParams)
    var tryRes: Try[SidechainStateStorage] = null
    val expectedException = new IllegalArgumentException("on update exception")

    // Test1: get one item
    assertEquals("Storage must return existing Box.", boxList(3), stateStorage.getBox(boxList(3).id()).get)

    // Test 2: try get non-existing item
    assertEquals("Storage must NOT contain requested Box.", None, stateStorage.getBox("non-existing id".getBytes(StandardCharsets.UTF_8)))

    // Data for Test 1:
    val version: ByteArrayWrapper = getVersion
    val toUpdate = new JArrayList[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    val toRemove = new JArrayList[ByteArrayWrapper]()
    toUpdate.add(storedBoxList.head)

    // withdrawals info
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.withdrawalEpochInformationKey),
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfo))))

    // Certificate
    val referenceEpochNumber = 0
    val cert: WithdrawalEpochCertificate = generateWithdrawalEpochCertificate(epochNumber = Some(referenceEpochNumber))
    val mainChainHash: MainchainHeaderHash = generateRandomMainchainHash()

    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getLastCertificateSidechainBlockIdKey), version))

    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getLastCertificateEpochNumberKey),
      new ByteArrayWrapper(new ByteArrayWrapper(Ints.toByteArray(referenceEpochNumber)))))

    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getTopQualityCertificateKey(referenceEpochNumber)),
      new ByteArrayWrapper(WithdrawalEpochCertificateSerializer.toBytes(cert))))

    //crosschain messages
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getCrosschainMessagesEpochCounterKey(withdrawalEpochInfo.epoch)),
      new ByteArrayWrapper(Ints.toByteArray(0))))
    val ccMessages = new JArrayList[CrossChainMessage]()
    ccMessages.add(crossChainMessages.last)
    //store also every  single hash separately with its epoch
    val singleMessageHash = ccMessages.get(0).getCrossChainMessageHash
    toUpdate.add(new Pair(stateStorage.getCrosschainMessageSingleKey(singleMessageHash),
      new ByteArrayWrapper(Ints.toByteArray(withdrawalEpochInfo.epoch))))
    toUpdate.add(new Pair(stateStorage.getCrosschainMessagesKey(withdrawalEpochInfo.epoch, 0),
      new ByteArrayWrapper(crossChainMessagesSerializer.toBytes(ccMessages))))
    //mainchain hashes
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getTopQualityCertificateMainchainHeaderKey(referenceEpochNumber)),
      new ByteArrayWrapper(mainChainHash.value)))

    // block fee info
    val nextBlockFeeInfoCounter: Int = 0
    val blockFeeInfo: BlockFeeInfo = BlockFeeInfo(100, getPrivateKey25519("1234".getBytes(StandardCharsets.UTF_8)).publicImage())
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getBlockFeeInfoCounterKey(withdrawalEpochInfo.epoch)),
      new ByteArrayWrapper(Ints.toByteArray(nextBlockFeeInfoCounter))))
    toUpdate.add(new Pair(new ByteArrayWrapper(stateStorage.getBlockFeeInfoKey(withdrawalEpochInfo.epoch, nextBlockFeeInfoCounter)),
      new ByteArrayWrapper(BlockFeeInfoSerializer.toBytes(blockFeeInfo))))

    // consensus epoch
    toUpdate.add(new Pair(stateStorage.consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(consensusEpoch))))
    toRemove.add(storedBoxList(2).getKey)

    //forger list indexes
    toUpdate.add(new Pair(stateStorage.forgerListIndexKey, new ByteArrayWrapper(Array[Byte](0.toByte))))

    Mockito.when(mockedPhysicalStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper, ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(args => {
        val actualVersion = args.getArgument(0).asInstanceOf[ByteArrayWrapper]
        val actualToUpdate = args.getArgument(1).asInstanceOf[java.util.List[Pair[ByteArrayWrapper, ByteArrayWrapper]]]
        val actualToRemove = args.getArgument(2).asInstanceOf[java.util.List[ByteArrayWrapper]]
        assertEquals("StateStorage.update(...) actual Version is wrong.", version, actualVersion)
        assertEquals("StateStorage.update(...) actual toUpdate list is wrong.", toUpdate, actualToUpdate)
        assertEquals("StateStorage.update(...) actual toRemove list is wrong.", toRemove, actualToRemove)
      })
      // For Test 2:
      .thenAnswer(_ => throw expectedException)

    // Test 1: test successful update
    tryRes = stateStorage.update(version, withdrawalEpochInfo, Set(boxList.head), Set(new ByteArrayWrapper(boxList(2).id())), Seq(),
      crossChainMessages, Seq(), Seq(), consensusEpoch, Seq((cert, mainChainHash)),  blockFeeInfo, None, false, new Array[Int](0), 0)
    assertTrue("StateStorage successful update expected, instead exception occurred:\n %s".format(if (tryRes.isFailure) tryRes.failed.get.getMessage else ""),
      tryRes.isSuccess)

    // Test 2: test failed update, when Storage throws an exception
    val box = getZenBox
    tryRes = stateStorage.update(version, withdrawalEpochInfo, Set(box), Set(new ByteArrayWrapper(boxList(3).id())),
      Seq(), Seq(), Seq(), Seq(), consensusEpoch, Seq(), blockFeeInfo, None, false, new Array[Int](0), 0)
    assertTrue("StateStorage failure expected during update.", tryRes.isFailure)
    assertEquals("StateStorage different exception expected during update.", expectedException, tryRes.failed.get)
    assertTrue("Storage should NOT contain Box that was tried to update.", stateStorage.getBox(box.id()).isEmpty)
    assertTrue("Storage should contain Box that was tried to remove.", stateStorage.getBox(boxList(3).id()).isDefined)
    assertEquals("Storage should return existing Box.", boxList(3), stateStorage.getBox(boxList(3).id()).get)
  }

  @Test
  def testRestoreNonCoinBoxes(): Unit = {
    //Create temporary SidechainStateStorage
    val stateStorageFile = temporaryFolder.newFolder("sidechainStateStorage")
    val stateStorage = new SidechainStateStorage(new VersionedLevelDbStorageAdapter(stateStorageFile), sidechainBoxesCompanion, params)

    //Create temporary BackupStorage
    val backupStorageFile = temporaryFolder.newFolder("backupStorage")
    val backupStorage = new BackupStorage(new VersionedLevelDbStorageAdapter(backupStorageFile), sidechainBoxesCompanion)

    //Fill BackUpStorage with 5 CustomBoxes and 1 random element
    customStoredBoxList.append(new Pair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper("key1".getBytes(StandardCharsets.UTF_8)), new ByteArrayWrapper("value1".getBytes(StandardCharsets.UTF_8))))
    backupStorage.update(getVersion, customStoredBoxList.asJava).get

    //Restore the SidechainStateStorage based on the BackupStorage
    stateStorage.restoreBackup(backupStorage.getBoxIterator, getVersion.data())

    //Read the SidechainStateStorage
    val storedBoxes = readStorage(new BoxIterator(stateStorage.getIterator, sidechainBoxesCompanion))

    //Verify that we did take only the 5 CustomBoxes
    assertEquals("SidechainStateStorage should contains only the 5 CustomBoxes!",storedBoxes.size(), 5)
    storedBoxes.forEach(box => {
      val storageElement = new Pair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper(box.getBoxKey), new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(box.getBox)))
      assertTrue("Restored boxes should be inside customStoredBoxList",customStoredBoxList.contains(storageElement))
      assertTrue("Restored boxes shouldn't be CoinBoxes!",!box.getBox.isInstanceOf[CoinsBox[_ <: PublicKey25519Proposition]])
    })
  }

  @Test
  def testRestoreCoinBoxes(): Unit = {
    //Create temporary SidechainStateStorage
    val stateStorageFile = temporaryFolder.newFolder("sidechainStateStorage")
    val stateStorage = new SidechainStateStorage(new VersionedLevelDbStorageAdapter(stateStorageFile), sidechainBoxesCompanion, params)

    //Create temporary BackupStorage
    val backupStorageFile = temporaryFolder.newFolder("backupStorage")
    val backupStorage = new BackupStorage(new VersionedLevelDbStorageAdapter(backupStorageFile), sidechainBoxesCompanion)

    //Fill BackUpStorage with 5 ZenBoxes and 1 random element
    storedBoxList.append(new Pair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper("key1".getBytes(StandardCharsets.UTF_8)), new ByteArrayWrapper("value1".getBytes(StandardCharsets.UTF_8))))
    backupStorage.update(getVersion, storedBoxList.asJava).get
    var exceptionThrown = false
    try {
      //Restore the SidechainStateStorage based on the BackupStorage
      stateStorage.restoreBackup(backupStorage.getBoxIterator, getVersion.data())
    } catch {
      case _:RuntimeException => exceptionThrown = true
    }
    assertTrue("CoinBoxes should not be restored!",exceptionThrown)
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

  @Test
  def testExceptions() : Unit = {
    var exceptionThrown = false

    try {
      val stateStorage = new SidechainStateStorage(null, sidechainBoxesCompanion, params)
    } catch {
      case e : IllegalArgumentException => exceptionThrown = true
    }

    assertTrue("SidechainStateStorage constructor. Exception must be thrown if storage is not specified.",
      exceptionThrown)

    exceptionThrown = false
    try {
      val stateStorage = new SidechainStateStorage(mockedPhysicalStorage, null, params)
    } catch {
      case e : IllegalArgumentException => exceptionThrown = true
    }

    assertTrue("SidechainStateStorage constructor. Exception must be thrown if boxesCompation is not specified.",
      exceptionThrown)

    val stateStorage = new SidechainStateStorage(mockedPhysicalStorage, sidechainBoxesCompanion, params)

    assertTrue("SidechainStorage.rollback. Method must return Failure if NULL version specified.",
      stateStorage.rollback(null).isFailure)
  }
}