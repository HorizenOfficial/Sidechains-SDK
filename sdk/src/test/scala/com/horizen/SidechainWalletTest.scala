package com.horizen

import com.horizen.block.SidechainBlock
import com.horizen.box._
import com.horizen.box.data.{BoxData, ZenBoxData}
import com.horizen.companion._
import com.horizen.consensus.{ConsensusEpochInfo, ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.params.{MainNetParams, NetworkParams}
import com.horizen.proposition._
import com.horizen.secret.{PrivateKey25519, Secret, SecretSerializer}
import com.horizen.storage._
import com.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import com.horizen.transaction.{BoxTransaction, RegularTransaction}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ForgingStakeMerklePathInfo, MerklePath, MerkleTree, Pair}
import com.horizen.wallet.ApplicationWallet
import org.junit.Assert._
import org.junit._
import org.junit.rules.TemporaryFolder
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.{VersionTag, bytesToId, bytesToVersion}
import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId

import java.lang.{Byte => JByte}
import java.nio.charset.StandardCharsets
import java.util
import java.util.{ArrayList => JArrayList, HashMap => JHashMap, List => JList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Random, Success, Try}

class SidechainWalletTest
  extends JUnitSuite
    with SidechainRelatedMainchainOutputFixture
    with TransactionFixture
    with CompanionsFixture
    with StoreFixture
    with MockitoSugar
{
  val mockedBoxStorage: Storage = mock[Storage]
  val mockedSecretStorage: Storage = mock[Storage]
  val mockedTransactionStorage: Storage = mock[Storage]
  val mockedForgingBoxesMerklePathStorage: Storage = mock[Storage]
  val mockedCswDataStorage: Storage = mock[Storage]

  val boxList = new ListBuffer[WalletBox]()
  val storedBoxList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
  val boxVersions = new ListBuffer[ByteArrayWrapper]()
  val boxToRestoreList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  val secretList = new ListBuffer[Secret]()
  val customSecretList = new ListBuffer[Secret]()
  val storedSecretList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
  val secretVersions = new ListBuffer[ByteArrayWrapper]()

  val storedTransactionList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  var customBoxesSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]] = new JHashMap()
  customBoxesSerializers.put(CustomBox.BOX_TYPE_ID, CustomBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
  val sidechainBoxesCompanion = SidechainBoxesCompanion(customBoxesSerializers)

  val customSecretSerializers: JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]] = new JHashMap()
  customSecretSerializers.put(CustomPrivateKey.SECRET_TYPE_ID, CustomPrivateKeySerializer.getSerializer.asInstanceOf[SecretSerializer[SidechainTypes#SCS]])
  val sidechainSecretsCompanion = SidechainSecretsCompanion(customSecretSerializers)

  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getDefaultTransactionsCompanion

  val withdrawalEpochNumber: Int = 1

  val params = MainNetParams()

  val _temporaryFolder = new TemporaryFolder()

  @Rule  def temporaryFolder = _temporaryFolder

  def boxIdToMerklePath(boxId: Array[Byte]): Array[Byte] = BytesUtils.reverseBytes(boxId)

  @Before
  def setUp() : Unit = {

    // Set base Secrets data
    secretList ++= getPrivateKey25519List(5).asScala
    customSecretList ++= getCustomPrivateKeyList(5).asScala

    secretVersions += getVersion

    for (s <- secretList) {
      storedSecretList.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(s.publicImage().bytes))
        val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))
        new Pair(key, value)
      })
    }


    // Mock get and update methods of SecretStorage
    Mockito.when(mockedSecretStorage.getAll).thenReturn(storedSecretList.asJava)

    Mockito.when(mockedSecretStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedSecretList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedSecretStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedSecretList.filter(p => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })

    Mockito.when(mockedSecretStorage.update(ArgumentMatchers.any[ByteArrayWrapper](),
        ArgumentMatchers.anyList[Pair[ByteArrayWrapper,ByteArrayWrapper]](),
        ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        secretVersions.append(answer.getArgument(0))
        for (s <- answer.getArgument(2).asInstanceOf[JList[ByteArrayWrapper]].asScala) {
          val index = storedSecretList.indexWhere(p => p.getKey.equals(s))
          if (index != -1)
            storedSecretList.remove(index)
        }
        for (s <- answer.getArgument(1).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala) {
          val index = storedSecretList.indexWhere(p => p.getKey.equals(s.getKey))
          if (index != -1)
            storedSecretList.remove(index)
        }
        storedSecretList.appendAll(answer.getArgument(1).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala)
      })


    // Set base WalletBox data
    boxList ++= getWalletBoxList(getZenBoxList(secretList.map(_.asInstanceOf[PrivateKey25519]).asJava)).asScala
    boxList += getWalletBox(getForgerBox(secretList.head.asInstanceOf[PrivateKey25519].publicImage()))

    val customBoxList = new ListBuffer[SidechainTypes#SCB]()
    customBoxList ++= getCustomBoxListWithPrivateKeys(customSecretList.map(_.asInstanceOf[CustomPrivateKey]).asJava).asScala.map(_.asInstanceOf[SidechainTypes#SCB])
    customBoxList += getCustomBox.asInstanceOf[SidechainTypes#SCB] //This box shouldn't be included in the 'scanBackup' test result

    boxVersions += getVersion

    for (b <- boxList) {
      storedBoxList.append({
        val wbs = new WalletBoxSerializer(sidechainBoxesCompanion)
        val key = new ByteArrayWrapper(Blake2b256.hash(b.box.id()))
        val value = new ByteArrayWrapper(wbs.toBytes(b))
        new Pair(key,value)
      })
    }

    for (b <- customBoxList) {
      boxToRestoreList.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(b.id()))
        val value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))
        new Pair(key,value)
      })
    }

    // Mock get and update methods of BoxStorage
    Mockito.when(mockedBoxStorage.getAll).thenReturn(storedBoxList.asJava)

    Mockito.when(mockedBoxStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedBoxList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedBoxStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        storedBoxList.filter(p => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })

    Mockito.when(mockedBoxStorage.update(ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.anyList[Pair[ByteArrayWrapper,ByteArrayWrapper]](),
      ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        boxVersions.append(answer.getArgument(0))
        for (s <- answer.getArgument(2).asInstanceOf[JList[ByteArrayWrapper]].asScala) {
          storedBoxList.remove(storedBoxList.indexWhere(p => p.getKey.equals(s)))
        }
        for (s <- answer.getArgument(1).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala) {
          val index = storedBoxList.indexWhere(p => p.getKey.equals(s.getKey))
          if (index != -1)
            storedBoxList.remove(index)
        }
        storedBoxList.appendAll(answer.getArgument(1).asInstanceOf[JList[Pair[ByteArrayWrapper,ByteArrayWrapper]]].asScala)
      })


  }

  @Test
  def testScanPersistent(): Unit = {
    val mockedWalletBoxStorage: SidechainWalletBoxStorage = mock[SidechainWalletBoxStorage]
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesInfoStorage: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedCswDataProvider: SidechainWalletCswDataProvider = mock[SidechainWalletCswDataProvider]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))
    val mockedApplicationWallet: ApplicationWallet = mock[ApplicationWallet]

    val sidechainWallet = new SidechainWallet("seed".getBytes(StandardCharsets.UTF_8),
      mockedWalletBoxStorage,
      mockedSecretStorage,
      mockedWalletTransactionStorage,
      mockedForgingBoxesInfoStorage,
      mockedCswDataProvider,
      params,
      mockedVersion,
      mockedApplicationWallet)

    // Prepare list of transactions:

    // We need to be sure that Txs box ids to be opened which are not related to the wallet
    // will be passed as a part of BoxIdsToRemove to the Storages and ApplicationWallet.
    val secretNotFromWallet = getPrivateKey25519
    val zenBoxNotFormWallet = getZenBox(secretNotFromWallet, 100, 100)

    val transaction1 = getRegularTransaction(boxList.slice(0, 3).map(_.box.asInstanceOf[ZenBox]) += zenBoxNotFormWallet,
      secretList.map(_.asInstanceOf[PrivateKey25519]) += secretNotFromWallet, secretList.slice(4, 5).map(_.publicImage().asInstanceOf[PublicKey25519Proposition]))
    val transaction2 = getRegularTransaction(boxList.slice(3, 5).map(_.box.asInstanceOf[ZenBox]),
      secretList.map(_.asInstanceOf[PrivateKey25519]),
      Seq(secretList(1)).map(_.publicImage().asInstanceOf[PublicKey25519Proposition]),
      Seq(),
      Seq(secretList(3)).map(_.publicImage().asInstanceOf[PublicKey25519Proposition]))

    // create mocked SidechainBlock
    val blockId = new Array[Byte](32)
    Random.nextBytes(blockId)
    val mockedBlock : SidechainBlock = mock[SidechainBlock]

    Mockito.when(mockedBlock.transactions)
      .thenReturn(Seq(
        transaction1.asInstanceOf[BoxTransaction[Proposition, Box[Proposition]]],
        transaction2.asInstanceOf[BoxTransaction[Proposition, Box[Proposition]]]
      ))

    val blockTimestamp = 123456789L
    Mockito.when(mockedBlock.timestamp).thenReturn(blockTimestamp)

    Mockito.when(mockedBlock.mainchainBlockReferencesData).thenReturn(Seq())

    Mockito.when(mockedBlock.id)
      .thenReturn(bytesToId(blockId))

    // Prepare mockedSecretStorage1 Secrets
    Mockito.when(mockedSecretStorage.getAll).thenReturn(secretList.toList)

    // Define fee payment boxes to be added during scan persistent:
    // 1 box related to Wallet, 2 boxes - not.
    val feePaymentBoxes: Seq[ZenBox] = (getZenBoxList(2).asScala += getZenBox(secretList.head.asInstanceOf[PrivateKey25519], 100, 100))

    // Test:
    // Prepare what we expect to receive for WalletBoxStorage.update
    Mockito.when(mockedWalletBoxStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[List[WalletBox]](),
      ArgumentMatchers.any[List[Array[Byte]]]()))
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        val walletBoxUpdateList = answer.getArgument(1).asInstanceOf[List[WalletBox]]
        val boxIdsRemoveList = answer.getArgument(2).asInstanceOf[List[Array[Byte]]]

        // check
        assertEquals("ScanPersistent on WalletBoxStorage.update(...) actual version is wrong.", new ByteArrayWrapper(blockId), version)
        assertEquals("ScanPersistent on WalletBoxStorage.update(...) actual walletBoxUpdateList is wrong.", List(
          new WalletBox(transaction1.newBoxes().get(0), ModifierId @@ transaction1.id,blockTimestamp),
          new WalletBox(transaction2.newBoxes().get(0), ModifierId @@ transaction2.id, blockTimestamp),
          new WalletBox(transaction2.newBoxes().get(1), ModifierId @@ transaction2.id, blockTimestamp),
          new WalletBox(feePaymentBoxes.last, blockTimestamp)
        ), walletBoxUpdateList)

        assertEquals("ScanPersistent on WalletBoxStorage.update(...) actual boxIdsRemoveList is wrong.",
          List(transaction1, transaction2).flatMap(_.unlockers().asScala.map(u => new ByteArrayWrapper(u.closedBoxId))),
          boxIdsRemoveList.map(new ByteArrayWrapper(_)))

        Try {
          mockedWalletBoxStorage
        }
      })

    Mockito.when(mockedWalletBoxStorage.getAll)
        .thenReturn(boxList.toList)

    // Prepare what we expect to receive for ApplicationWallet.onChangeBoxes
    Mockito.when(mockedApplicationWallet.onChangeBoxes(
      ArgumentMatchers.any[Array[Byte]](),
      ArgumentMatchers.anyList[SidechainTypes#SCB](),
      ArgumentMatchers.anyList[Array[Byte]]()))
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[Array[Byte]]
        val boxesToUpdate = answer.getArgument(1).asInstanceOf[JList[SidechainTypes#SCB]]
        val boxIdsToRemove = answer.getArgument(2).asInstanceOf[JList[Array[Byte]]].asScala.map(new ByteArrayWrapper(_)).toList.asJava

        // check
        assertEquals("ScanPersistent on ApplicationWallet.onChangeBoxes(...) actual version is wrong.",
          new ByteArrayWrapper(blockId),
          new ByteArrayWrapper(version))

        assertEquals("ScanPersistent on ApplicationWallet.onChangeBoxes(...) actual boxesToUpdate list is wrong.", util.Arrays.asList(
          transaction1.newBoxes().get(0),
          transaction2.newBoxes().get(0),
          transaction2.newBoxes().get(1),
          feePaymentBoxes.head,
          feePaymentBoxes(1),
          feePaymentBoxes(2)
        ), boxesToUpdate)

        assertEquals("ScanPersistent on ApplicationWallet.onChangeBoxes(...) actual boxIdsToRemove list is wrong.",
          List(transaction1, transaction2).flatMap(_.unlockers().asScala.map(u => new ByteArrayWrapper(u.closedBoxId))).asJava,
          boxIdsToRemove)

      })

    Mockito.when(mockedWalletTransactionStorage.update(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[List[SidechainTypes#SCBT]]()))
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        val transactionUpdateList = answer.getArgument(1).asInstanceOf[List[SidechainTypes#SCBT]]

        assertEquals("ScanPersistent on WalletTransactionStorage.update(...) actual version is wrong.", new ByteArrayWrapper(blockId), version)

        assertEquals("ScanPersistent on WalletTransactionStorage.update(...) actual transactionUpdateList list is wrong.",
          List(transaction1, transaction2),
          transactionUpdateList)

        Try {
          mockedWalletTransactionStorage
        }
      })

    Mockito.when(mockedForgingBoxesInfoStorage.updateForgerBoxes(
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[Seq[ForgerBox]],
      ArgumentMatchers.any[Seq[Array[Byte]]]))
        .thenAnswer(answer => {
          val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
          val forgerBoxesToAppend = answer.getArgument(1).asInstanceOf[Seq[ForgerBox]]
          val boxIdsRemoveList = answer.getArgument(2).asInstanceOf[List[Array[Byte]]]
          assertEquals("ScanPersistent on ForgingBoxesInfoStorage.updateForgerBoxes(...) actual version is wrong.", new ByteArrayWrapper(blockId), version)
          assertEquals("ScanPersistent on ForgingBoxesInfoStorage.updateForgerBoxes(...) actual toAppend seq is wrong.", 1,  forgerBoxesToAppend.size)

          assertEquals("ScanPersistent on ForgingBoxesInfoStorage.updateForgerBoxes(...) actual boxIdsRemoveList is wrong.",
            List(transaction1, transaction2).flatMap(_.unlockers().asScala.map(u => new ByteArrayWrapper(u.closedBoxId))),
            boxIdsRemoveList.map(new ByteArrayWrapper(_)))

          Success(mockedForgingBoxesInfoStorage)
        })

    Mockito.when(mockedCswDataProvider.update(
      ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[Int],
      ArgumentMatchers.any[NetworkParams],
      ArgumentMatchers.any[SidechainWallet],
      ArgumentMatchers.any[Option[UtxoMerkleTreeView]]))
      .thenAnswer(answer => {
        val modifier = answer.getArgument(0).asInstanceOf[SidechainBlock]
        val version = answer.getArgument(1).asInstanceOf[ByteArrayWrapper]
        val withdrawalEpoch = answer.getArgument(2).asInstanceOf[Int]
        val netParam = answer.getArgument(3).asInstanceOf[NetworkParams]
        val wallet = answer.getArgument(4).asInstanceOf[SidechainWallet]
        val utxoMerkleTreeViewOpt = answer.getArgument(5).asInstanceOf[Option[UtxoMerkleTreeView]]

        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual modifier is wrong.",
          mockedBlock, modifier)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual version is wrong.",
          new ByteArrayWrapper(blockId), version)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) withdrawalEpoch version is wrong.",
          withdrawalEpochNumber, withdrawalEpoch)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual NetworkParams are wrong.",
          params, netParam)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual SidechainWallet is wrong.",
          sidechainWallet, wallet)
        assertFalse("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual UtxoMerkleTreeView is defined.",
          utxoMerkleTreeViewOpt.isDefined)

        Success(mockedCswDataProvider)
      })

    sidechainWallet.scanPersistent(mockedBlock, withdrawalEpochNumber, feePaymentBoxes, None)


    // Test: Last WE block
    val mockedUtxoMerkleTreeView: UtxoMerkleTreeView = mock[UtxoMerkleTreeView]
    Mockito.when(mockedUtxoMerkleTreeView.utxoMerklePath(ArgumentMatchers.any[Array[Byte]]())).thenAnswer(args => {
      val boxId: Array[Byte] = args.getArgument(0)
      Some(boxIdToMerklePath(boxId))
    })

    Mockito.reset(mockedCswDataProvider)
    Mockito.when(mockedCswDataProvider.update(
      ArgumentMatchers.any[SidechainBlock],
      ArgumentMatchers.any[ByteArrayWrapper](),
      ArgumentMatchers.any[Int],
      ArgumentMatchers.any[NetworkParams],
      ArgumentMatchers.any[SidechainWallet],
      ArgumentMatchers.any[Option[UtxoMerkleTreeView]]))
      .thenAnswer(answer => {
        val modifier = answer.getArgument(0).asInstanceOf[SidechainBlock]
        val version = answer.getArgument(1).asInstanceOf[ByteArrayWrapper]
        val withdrawalEpoch = answer.getArgument(2).asInstanceOf[Int]
        val netParam = answer.getArgument(3).asInstanceOf[NetworkParams]
        val wallet = answer.getArgument(4).asInstanceOf[SidechainWallet]
        val utxoMerkleTreeView = answer.getArgument(5).asInstanceOf[Option[UtxoMerkleTreeView]].get


        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual modifier is wrong.",
          mockedBlock, modifier)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual version is wrong.",
          new ByteArrayWrapper(blockId), version)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) withdrawalEpoch version is wrong.",
          withdrawalEpochNumber, withdrawalEpoch)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual NetworkParams are wrong.",
          params, netParam)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual SidechainWallet is wrong.",
          sidechainWallet, wallet)
        assertEquals("ScanPersistent on SidechainWalletCswDataStorage.update(...) actual UtxoMerkleTreeView is wrong.",
          mockedUtxoMerkleTreeView, utxoMerkleTreeView)

        Success(mockedCswDataProvider)
      })

    sidechainWallet.scanPersistent(mockedBlock, withdrawalEpochNumber, feePaymentBoxes, Some(mockedUtxoMerkleTreeView))
  }

  @Test
  def testScanBackUpNonCoinBoxes(): Unit = {
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesInfoStorage: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedCswDataProvider: SidechainWalletCswDataProvider = mock[SidechainWalletCswDataProvider]
    val mockedApplicationWallet: ApplicationWallet = mock[ApplicationWallet]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))

    Mockito.when(mockedSecretStorage.getAll).thenAnswer(_=>customSecretList.toList)

    //Create temporary WalletBoxStorage
    val walletBoxStorageFile = temporaryFolder.newFolder("walletBoxStorage")
    val walletBoxStorage = new SidechainWalletBoxStorage(new VersionedLevelDbStorageAdapter(walletBoxStorageFile), sidechainBoxesCompanion)

    //Create temporary BackupStorage
    val backupStorageFile = temporaryFolder.newFolder("backupStorage")
    val backupStorage = new BackupStorage(new VersionedLevelDbStorageAdapter(backupStorageFile), sidechainBoxesCompanion)

    boxToRestoreList.append(new Pair[ByteArrayWrapper, ByteArrayWrapper](new ByteArrayWrapper("key1".getBytes(StandardCharsets.UTF_8)), new ByteArrayWrapper("value1".getBytes(StandardCharsets.UTF_8))))
    backupStorage.update(getVersion, boxToRestoreList.asJava).get

    val sidechainWallet = new SidechainWallet("seed".getBytes(StandardCharsets.UTF_8),
      walletBoxStorage,
      mockedSecretStorage,
      mockedWalletTransactionStorage,
      mockedForgingBoxesInfoStorage,
      mockedCswDataProvider,
      params,
      mockedVersion,
      mockedApplicationWallet)

    // Mock get and update methods of SecretStorage
    sidechainWallet.scanBackUp(backupStorage.getBoxIterator, System.currentTimeMillis())

    assertTrue("Box stored to the backupStorage should be 7: 5 regular box + 1 new address + 1 fake ",boxToRestoreList.size == 7)
    val storedBoxes = readStorage(walletBoxStorage)

    //Verify that we did take only the 5 Boxes
    assertEquals("SidechainWalletBoxStorage should contains only the 5 CustomBoxes!",5, storedBoxes.size())
    val publicKeys = customSecretList.map(_.asInstanceOf[CustomPrivateKey].publicImage()).asJava
    storedBoxes.forEach(box => {
      assertTrue("Restored Boxes propositions should be inside our wallet!", publicKeys.contains(box.box.proposition()))
    })
  }

  @Test
  def testScanBackUpCoinBoxes(): Unit = {
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesInfoStorage: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedCswDataProvider: SidechainWalletCswDataProvider = mock[SidechainWalletCswDataProvider]
    val mockedApplicationWallet: ApplicationWallet = mock[ApplicationWallet]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))
    Mockito.when(mockedSecretStorage.getAll).thenAnswer(_=>secretList.toList)

    //Create temporary WalletBoxStorage
    val walletBoxStorageFile = temporaryFolder.newFolder("walletBoxStorage")
    val walletBoxStorage = new SidechainWalletBoxStorage(new VersionedLevelDbStorageAdapter(walletBoxStorageFile), sidechainBoxesCompanion)

    //Create temporary BackupStorage
    val backupStorageFile = temporaryFolder.newFolder("backupStorage")
    val backupStorage = new BackupStorage(new VersionedLevelDbStorageAdapter(backupStorageFile), sidechainBoxesCompanion)

    //Serialize ZenBoxes
    val zenBoxSerializedList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()
    for (b <- boxList) {
      zenBoxSerializedList.append({
        val key = new ByteArrayWrapper(Blake2b256.hash(b.box.id()))
        val value = new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b.box))
        new Pair(key,value)
      })
    }
    backupStorage.update(getVersion, zenBoxSerializedList.asJava).get

    val sidechainWallet = new SidechainWallet("seed".getBytes(StandardCharsets.UTF_8),
      walletBoxStorage,
      mockedSecretStorage,
      mockedWalletTransactionStorage,
      mockedForgingBoxesInfoStorage,
      mockedCswDataProvider,
      params,
      mockedVersion,
      mockedApplicationWallet)

    var exceptionThrown = false
    sidechainWallet.scanBackUp(backupStorage.getBoxIterator, System.currentTimeMillis()) match {
      case Failure(_) =>
        exceptionThrown = true
      case Success(_) =>
        fail()
    }
    assertTrue("CoinBoxes should not be restored!",exceptionThrown)
  }

  def readStorage(walletBoxStorage: SidechainWalletBoxStorage): JArrayList[WalletBox] = {
    val walletBoxStorageIterator: StorageIterator = walletBoxStorage.getIterator
    walletBoxStorageIterator.seekToFirst()

    val walletBoxSerializer: WalletBoxSerializer = new WalletBoxSerializer(sidechainBoxesCompanion)
    val storedBoxes = new JArrayList[WalletBox]()
    while(walletBoxStorageIterator.hasNext) {
      val entry = walletBoxStorageIterator.next()
      val box: Try[WalletBox] = walletBoxSerializer.parseBytesTry(entry.getValue)

      if(box.isSuccess) {
        val currBox: WalletBox = box.get
        storedBoxes.add(currBox)
      }
    }
    storedBoxes
  }

  @Test
  def testRollback(): Unit = {
    val mockedWalletBoxStorage: SidechainWalletBoxStorage = mock[SidechainWalletBoxStorage]
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesMerklePathStorage: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedSidechainWalletCswDataProvider: SidechainWalletCswDataProvider = mock[SidechainWalletCswDataProvider]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))
    val mockedApplicationWallet: ApplicationWallet = mock[ApplicationWallet]

    val sidechainWallet = new SidechainWallet(
      "seed".getBytes(StandardCharsets.UTF_8),
      mockedWalletBoxStorage,
      mockedSecretStorage,
      mockedWalletTransactionStorage,
      mockedForgingBoxesMerklePathStorage,
      mockedSidechainWalletCswDataProvider,
      params,
      mockedVersion,
      mockedApplicationWallet)

    val expectedException = new IllegalArgumentException("on rollback exception")
    var rollbackEventOccurred = false

    // Prepare block ID and corresponding version
    val blockId = new Array[Byte](32)
    Random.nextBytes(blockId)
    val versionTag: VersionTag = VersionTag @@ BytesUtils.toHexString(blockId)

    // Prepare what we expect to receive in WalletBoxStorage.rollback
    Mockito.when(mockedWalletBoxStorage.rollback(
      ArgumentMatchers.any[ByteArrayWrapper]()))
      // For Test 1:
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]

        assertEquals("Rollback on WalletBoxStorage.rollback(...) actual version is wrong.",
          new ByteArrayWrapper(blockId),
          version)
        Success(mockedWalletBoxStorage)
      })
      // For Test 2:
      .thenAnswer(_ => throw expectedException)

    // Prepare what we expect to receive in ApplicationWallet.onRollback
    Mockito.when(mockedApplicationWallet.onRollback(
      ArgumentMatchers.any[Array[Byte]]()))
      // For Test 1:
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[Array[Byte]]

        assertEquals("Rollback on ApplicationWallet.onRollback(...) actual version is wrong.",
          new ByteArrayWrapper(blockId),
          new ByteArrayWrapper(version))
      })
      // For Test 2:
      .thenAnswer(_ => rollbackEventOccurred = true)


    // Prepare what we expect to receive in SidechainWalletTransactionStorage.rollback
    Mockito.when(mockedWalletTransactionStorage.rollback(
      ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        assertEquals("Rollback on SidechainWalletTransactionStorage.rollback(...) actual version is wrong.",
          new ByteArrayWrapper(blockId), version)

        Success(mockedWalletTransactionStorage)
    })

    // Prepare what we expect to receive in ForgingBoxesInfoStorage.rollback
    Mockito.when(mockedForgingBoxesMerklePathStorage.rollback(
      ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        assertEquals("Rollback on ForgingBoxesInfoStorage.rollback(...) actual version is wrong.",
          new ByteArrayWrapper(blockId), version)

        Success(mockedForgingBoxesMerklePathStorage)
      })

    // Prepare what we expect to receive in SidechainWalletCswDataStorage.rollback
    Mockito.when(mockedSidechainWalletCswDataProvider.rollback(
      ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer(answer => {
        val version = answer.getArgument(0).asInstanceOf[ByteArrayWrapper]
        assertEquals("Rollback on mockedSidechainWalletCswDataProvider.rollback(...) actual version is wrong.",
          new ByteArrayWrapper(blockId), version)

        Success(mockedSidechainWalletCswDataProvider)
      })


    // Test 1: successful rollback
    assertTrue("SidechainWallet rollback expected to be successful", sidechainWallet.rollback(versionTag).isSuccess)


    // Test 2: failed to rollback, WalletBoxStorage.rollback(...) threw an exception
    val res = sidechainWallet.rollback(versionTag)
    assertTrue("SidechainWallet failure expected during rollback.", res.isFailure)
    assertEquals("SidechainWallet different exception expected during rollback.", expectedException, res.failed.get)
    assertFalse("ApplicationWallet onRollback(...) event NOT expected.", rollbackEventOccurred)
  }

  @Test
  def testScanPersistentIntegration() : Unit = {
    val mockedBlock : SidechainBlock = mock[SidechainBlock]
    val blockId = Array[Byte](32)
    val from : JList[Pair[ZenBox, PrivateKey25519]] = new JArrayList()
    val to: JList[BoxData[_ <: Proposition, _ <: Box[_ <: Proposition]]] = new JArrayList()

    Random.nextBytes(blockId)

    var inputsAmount: Long = 0L
    for (i <- 0 to 2) {
      from.add(new Pair(boxList(i).box.asInstanceOf[ZenBox], secretList(i).asInstanceOf[PrivateKey25519]))
      inputsAmount += boxList(i).box.value()
    }

    var outputsAmount: Long = 0L
    for (i <- 0 to 2) {
      val value = 10L
      to.add(new ZenBoxData(secretList(i).publicImage().asInstanceOf[PublicKey25519Proposition], value))
      outputsAmount += value
    }

    val tx : RegularTransaction = RegularTransaction.create(from, to, inputsAmount - outputsAmount)

    Mockito.when(mockedBlock.transactions)
      .thenReturn(Seq(tx.asInstanceOf[BoxTransaction[Proposition, Box[Proposition]]]))

    Mockito.when(mockedBlock.mainchainBlockReferencesData).thenReturn(Seq())

    Mockito.when(mockedBlock.id)
      .thenReturn(bytesToId(blockId))

    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))

    val sidechainWallet = new SidechainWallet("seed".getBytes(StandardCharsets.UTF_8), new SidechainWalletBoxStorage(mockedBoxStorage, sidechainBoxesCompanion),
      new SidechainSecretStorage(mockedSecretStorage, sidechainSecretsCompanion),
      new SidechainWalletTransactionStorage(mockedTransactionStorage, sidechainTransactionsCompanion),
      new ForgingBoxesInfoStorage(mockedForgingBoxesMerklePathStorage),
      SidechainWalletCswDataProviderCSWEnabled(new SidechainWalletCswDataStorage(mockedCswDataStorage)),
      params,
      mockedVersion,
      new CustomApplicationWallet())

    sidechainWallet.scanPersistent(mockedBlock, withdrawalEpochNumber, Seq(), None)

    val wbl = sidechainWallet.boxes()

    assertEquals("Wallet must contain specified count of WalletBoxes.", boxList.size, wbl.size)
    assertTrue("Wallet must contain all specified WalletBoxes.",
      wbl.asJavaCollection.containsAll(boxList.slice(3, 5).asJavaCollection))
    assertTrue("Wallet must contain all specified WalletBoxes.",
      wbl.map(_.box).asJavaCollection.containsAll(tx.newBoxes))

  }

  @Test
  def testScanPersistentWithCSWDisabled() : Unit = {
    val mockedBlock : SidechainBlock = mock[SidechainBlock]
    val blockId = Array[Byte](32)
    val from : JList[Pair[ZenBox, PrivateKey25519]] = new JArrayList()
    val to: JList[BoxData[_ <: Proposition, _ <: Box[_ <: Proposition]]] = new JArrayList()

    Random.nextBytes(blockId)

    var inputsAmount: Long = 0L
    for (i <- 0 to 2) {
      from.add(new Pair(boxList(i).box.asInstanceOf[ZenBox], secretList(i).asInstanceOf[PrivateKey25519]))
      inputsAmount += boxList(i).box.value()
    }

    var outputsAmount: Long = 0L
    for (i <- 0 to 2) {
      val value = 10L
      to.add(new ZenBoxData(secretList(i).publicImage().asInstanceOf[PublicKey25519Proposition], value))
      outputsAmount += value
    }

    val tx : RegularTransaction = RegularTransaction.create(from, to, inputsAmount - outputsAmount)

    Mockito.when(mockedBlock.transactions)
      .thenReturn(Seq(tx.asInstanceOf[BoxTransaction[Proposition, Box[Proposition]]]))

    Mockito.when(mockedBlock.mainchainBlockReferencesData).thenReturn(Seq())

    Mockito.when(mockedBlock.id)
      .thenReturn(bytesToId(blockId))
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))

    val sidechainWallet = new SidechainWallet("seed".getBytes(StandardCharsets.UTF_8), new SidechainWalletBoxStorage(mockedBoxStorage, sidechainBoxesCompanion),
      new SidechainSecretStorage(mockedSecretStorage, sidechainSecretsCompanion),
      new SidechainWalletTransactionStorage(mockedTransactionStorage, sidechainTransactionsCompanion),
      new ForgingBoxesInfoStorage(mockedForgingBoxesMerklePathStorage),
      SidechainWalletCswDataProviderCSWDisabled(),
      MainNetParams(isCSWEnabled = false),
      mockedVersion,
      new CustomApplicationWallet())

    sidechainWallet.scanPersistent(mockedBlock, withdrawalEpochNumber, Seq(), None)

    val wbl = sidechainWallet.boxes()

    assertEquals("Wallet must contain specified count of WalletBoxes.", boxList.size, wbl.size)
    assertTrue("Wallet must contain all specified WalletBoxes.",
      wbl.asJavaCollection.containsAll(boxList.slice(3, 5).asJavaCollection))
    assertTrue("Wallet must contain all specified WalletBoxes.",
      wbl.map(_.box).asJavaCollection.containsAll(tx.newBoxes))

    assertTrue("Wallet must not contain CSW Data",sidechainWallet.getCswData(withdrawalEpochNumber).isEmpty)

  }



  @Test
  def testSecrets(): Unit = {
    val mockedWalletBoxStorage1: SidechainWalletBoxStorage = mock[SidechainWalletBoxStorage]
    val mockedSecretStorage1: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage1: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesMerklePathStorage1: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedCswDataStorage1: SidechainWalletCswDataStorage = mock[SidechainWalletCswDataStorage]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))
    val mockedApplicationWallet: ApplicationWallet = mock[ApplicationWallet]
    val sidechainWallet = new SidechainWallet("seed".getBytes(StandardCharsets.UTF_8), mockedWalletBoxStorage1, mockedSecretStorage1,
      mockedWalletTransactionStorage1, mockedForgingBoxesMerklePathStorage1, SidechainWalletCswDataProviderCSWEnabled(mockedCswDataStorage1), params, mockedVersion, mockedApplicationWallet)
    val secret1 = getPrivateKey25519("testSeed1".getBytes(StandardCharsets.UTF_8))
    val secret2 = getPrivateKey25519("testSeed2".getBytes(StandardCharsets.UTF_8))


    // Test 1: test secret(proposition) and secretByPublicKey25519Proposition(proposition)
    Mockito.when(mockedSecretStorage1.get(secret1.publicImage())).thenReturn(Some(secret1))
    Mockito.when(mockedSecretStorage1.getAll).thenReturn(List(secret1, secret2))

    var actualSecret = sidechainWallet.secret(secret1.publicImage()).get
    assertEquals("SidechainWallet failed to retrieve a proper Secret.", secret1, actualSecret)

    actualSecret = sidechainWallet.secretByPublicKey25519Proposition(secret1.publicImage()).get
    assertEquals("SidechainWallet failed to retrieve a proper Secret.", secret1, actualSecret)

    val retList =  sidechainWallet.secretsByProposition(secret1.publicImage())
    assertEquals("SidechainWallet failed to retrieve a proper Secret.", 1, retList.size())
    assertEquals("SidechainWallet failed to retrieve a proper Secret.", secret1, retList.get(0))


    // Test 2: test secrets(), publicKeys(), allSecrets(), secretsOfType(type)
    Mockito.when(mockedSecretStorage1.getAll).thenReturn(List(secret1, secret2))

    val actualSecrets = sidechainWallet.secrets()
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", Set(secret1, secret2), actualSecrets)

    val actualPublicKeys = sidechainWallet.publicKeys()
    assertEquals("SidechainWallet failed to retrieve a proper Public Keys.", Set(secret1.publicImage(), secret2.publicImage()), actualPublicKeys)

    val actualSecretsJava = sidechainWallet.allSecrets()
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", util.Arrays.asList(secret1, secret2), actualSecretsJava)

    val actualPrivateKeysJava = sidechainWallet.secretsOfType(classOf[PrivateKey25519])
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", util.Arrays.asList(secret1, secret2), actualPrivateKeysJava)

    val actualCustomKeysJava = sidechainWallet.secretsOfType(classOf[CustomPrivateKey])
    assertEquals("SidechainWallet failed to retrieve a proper Secrets.", util.Arrays.asList(), actualCustomKeysJava)


    // Test 3: try to add new valid Secret
    // ApplicationWallet.onAddSecret() event expected
    var onAddSecretEvent: Boolean = false
    Mockito.when(mockedSecretStorage1.add(secret1)).thenReturn(Try{mockedSecretStorage1})
    Mockito.when(mockedApplicationWallet.onAddSecret(secret1)).thenAnswer(_ => onAddSecretEvent = true)

    var result: Boolean = sidechainWallet.addSecret(secret1).isSuccess
    assertTrue("SidechainWallet failed to add new Secret.", result)
    assertTrue("ApplicationWallet onAddSecret() event should be emitted.", onAddSecretEvent)


    // Test 4: try to add null Secret
    // ApplicationWallet.onAddSecret() event NOT expected
    onAddSecretEvent = false
    assertTrue("SidechainWallet failure expected during adding NULL Secret.", sidechainWallet.addSecret(null).isFailure)
    assertFalse("ApplicationWallet onAddSecret() event should NOT be emitted.", onAddSecretEvent)


    // Test 5: try to add some Secret, exception in SecretStorage occurred.
    // ApplicationWallet.onAddSecret() event NOT expected
    var expectedException = new IllegalArgumentException("on add exception")
    Mockito.when(mockedSecretStorage1.add(secret1)).thenReturn(Failure(expectedException))

    var failureResult = sidechainWallet.addSecret(secret1)
    assertTrue("SidechainWallet failure expected during adding new Secret.", failureResult.isFailure)
    assertEquals("SidechainWallet different exception expected during adding new Secret.", expectedException, failureResult.failed.get)
    assertFalse("ApplicationWallet onAddSecret() event should NOT be emitted.", onAddSecretEvent)


    // Test 6: try to remove valid Secret
    // ApplicationWallet.onRemoveSecret() event expected
    var onRemoveSecretEvent: Boolean = false
    Mockito.when(mockedSecretStorage1.remove(secret1.publicImage())).thenReturn(Try{mockedSecretStorage1})
    Mockito.when(mockedApplicationWallet.onRemoveSecret(secret1.publicImage())).thenAnswer(_ => onRemoveSecretEvent = true)
    result = sidechainWallet.removeSecret(secret1.publicImage()).isSuccess
    assertTrue("SidechainWallet failed to remove Secret.", result)
    assertTrue("ApplicationWallet onRemoveSecret() event should be emitted.", onRemoveSecretEvent)


    // Test 7: try to remove null Secret
    // ApplicationWallet.onRemoveSecret() event NOT expected
    onRemoveSecretEvent = false
    assertTrue("SidechainWallet failure expected during removing NULL Secret.", sidechainWallet.removeSecret(null).isFailure)
    assertFalse("ApplicationWallet onRemoveSecret() event should NOT be emitted.", onRemoveSecretEvent)


    // Test 8: try to remove some Secret, exception in SecretStorage occurred.
    // ApplicationWallet.onRemoveSecret() event NOT expected
    onRemoveSecretEvent = false
    expectedException = new IllegalArgumentException("on remove exception")
    Mockito.when(mockedSecretStorage1.remove(secret1.publicImage())).thenReturn(Failure(expectedException))

    failureResult = sidechainWallet.removeSecret(secret1.publicImage())
    assertTrue("SidechainWallet failure expected during removing new Secret.", failureResult.isFailure)
    assertEquals("SidechainWallet different exception expected during removing new Secret.", expectedException, failureResult.failed.get)
    assertFalse("ApplicationWallet onRemoveSecret() event should NOT be emitted.", onRemoveSecretEvent)
  }

  @Test
  def testWalletBoxes(): Unit = {
    val mockedWalletBoxStorage1: SidechainWalletBoxStorage = mock[SidechainWalletBoxStorage]
    val mockedSecretStorage1: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage1: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesMerklePathStorage1: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedCswDataStorage1: SidechainWalletCswDataStorage = mock[SidechainWalletCswDataStorage]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))

    val sidechainWallet = new SidechainWallet("seed".getBytes(StandardCharsets.UTF_8), mockedWalletBoxStorage1, mockedSecretStorage1,
      mockedWalletTransactionStorage1, mockedForgingBoxesMerklePathStorage1, SidechainWalletCswDataProviderCSWEnabled(mockedCswDataStorage1), params, mockedVersion, new CustomApplicationWallet())
    val walletBoxZen1 = getWalletBox(classOf[ZenBox])
    val walletBoxZen2 = getWalletBox(classOf[ZenBox])
    val walletBoxZen3 = getWalletBox(classOf[ZenBox])
    val walletBoxCustom = getWalletBox(classOf[CustomBox])


    // Test 1: test test boxes(), allBoxes(), allBoxes(boxIdsToExclude)
    Mockito.when(mockedWalletBoxStorage1.getAll).thenReturn(List(walletBoxZen1, walletBoxZen2, walletBoxZen3, walletBoxCustom))

    val actualBoxes = sidechainWallet.boxes()
    assertEquals("SidechainWallet failed to retrieve a proper Boxes.",
      List(walletBoxZen1, walletBoxZen2, walletBoxZen3, walletBoxCustom), actualBoxes)

    val actualBoxesJava = sidechainWallet.allBoxes
    assertEquals("SidechainWallet failed to retrieve a proper Boxes.",
      util.Arrays.asList(walletBoxZen1.box, walletBoxZen2.box, walletBoxZen3.box, walletBoxCustom.box), actualBoxesJava)

    // exclude id of walletBoxZen1
    val actualBoxesWithExcludeJava = sidechainWallet.allBoxes(util.Arrays.asList(walletBoxZen1.box.id()))
    assertEquals("SidechainWallet failed to retrieve a proper Boxes with excluded ids.",
      util.Arrays.asList(walletBoxZen2.box, walletBoxZen3.box, walletBoxCustom.box), actualBoxesWithExcludeJava)


    // Test 2: test boxesOfType(type) and boxesOfType(type, boxIdsToExclude)
    Mockito.when(mockedWalletBoxStorage1.getByType(classOf[ZenBox])).thenReturn(List(walletBoxZen1, walletBoxZen2))
    Mockito.when(mockedWalletBoxStorage1.getByType(classOf[CustomBox])).thenReturn(List(walletBoxCustom))

    val actualBoxesByTypeJava = sidechainWallet.boxesOfType(classOf[ZenBox])
    assertEquals("SidechainWallet failed to retrieve a proper Boxes of type ZenBox.",
      util.Arrays.asList(walletBoxZen1.box, walletBoxZen2.box), actualBoxesByTypeJava)

    val actualCustomBoxesByTypeJava = sidechainWallet.boxesOfType(classOf[CustomBox])
    assertEquals("SidechainWallet failed to retrieve a proper Boxes of type CustomBox.",
      util.Arrays.asList(walletBoxCustom.box), actualCustomBoxesByTypeJava)

    val actualBoxesByTypeWithExcludeJava = sidechainWallet.boxesOfType(classOf[ZenBox], util.Arrays.asList(walletBoxZen1.box.id()))
    assertEquals("SidechainWallet failed to retrieve a proper Boxes of type ZenBox with excluded ids.",
      util.Arrays.asList(walletBoxZen2.box), actualBoxesByTypeWithExcludeJava)


    // Test 3: test boxesBalance(type)
    val balance = 100L
    Mockito.when(mockedWalletBoxStorage1.getBoxesBalance(classOf[ZenBox])).thenReturn(balance)

    val actualBalance = sidechainWallet.boxesBalance(classOf[ZenBox])
    assertEquals("SidechainWallet failed to retrieve a proper balance for type ZenBox.", balance, actualBalance)
  }

  @Test
  def testGetForgingStakeMerklePath(): Unit = {
    val mockedWalletBoxStorage: SidechainWalletBoxStorage = mock[SidechainWalletBoxStorage]
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesMerklePathStorage: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedSidechainWalletCswDataProvider: SidechainWalletCswDataProvider = mock[SidechainWalletCswDataProvider]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))
    val mockedApplicationWallet: ApplicationWallet = mock[ApplicationWallet]

    val sidechainWallet = new SidechainWallet(
      "seed".getBytes(StandardCharsets.UTF_8),
      mockedWalletBoxStorage,
      mockedSecretStorage,
      mockedWalletTransactionStorage,
      mockedForgingBoxesMerklePathStorage,
      mockedSidechainWalletCswDataProvider,
      params,
      mockedVersion,
      mockedApplicationWallet)


    val storedForgerBox: ForgerBox = boxList.last.box.asInstanceOf[ForgerBox]
    val storedMerklePathSeq: Seq[ForgingStakeMerklePathInfo] = Seq(ForgingStakeMerklePathInfo(
      ForgingStakeInfo(storedForgerBox.blockSignProposition(), storedForgerBox.vrfPubKey(), storedForgerBox.value()),
      new MerklePath(new JArrayList())
    ))


    // Note: consensus epoch calculation starts from 1 and contain only genesis block as the end of it.
    // Test 1: second epoch info - should request for the first epoch from storage
    val secondEpoch: ConsensusEpochNumber = ConsensusEpochNumber @@ 2
    var expectedEpochInfo: ConsensusEpochNumber = ConsensusEpochNumber @@ 1

    // Verify epoch number and return predefined merklePathSeq
    Mockito.when(mockedForgingBoxesMerklePathStorage.getForgingStakeMerklePathInfoForEpoch(
      ArgumentMatchers.any[ConsensusEpochNumber]()))
      .thenAnswer(answer => {
        val epoch = answer.getArgument(0).asInstanceOf[ConsensusEpochNumber]

        assertEquals("Different epoch number request expected.", expectedEpochInfo, epoch)
        Some(storedMerklePathSeq)
      })

    var forgingBoxMerklePathInfoSeq = sidechainWallet.getForgingStakeMerklePathInfoOpt(secondEpoch).get
    assertEquals("Merkle path seq size expected to be different.", storedMerklePathSeq.size, forgingBoxMerklePathInfoSeq.size)

    // Test 2: third epoch info - should request for the first epoch from storage
    val thirdEpoch = ConsensusEpochNumber @@ 3
    expectedEpochInfo = ConsensusEpochNumber @@ 1
    forgingBoxMerklePathInfoSeq = sidechainWallet.getForgingStakeMerklePathInfoOpt(thirdEpoch).get
    assertEquals("Merkle path seq size expected to be different.", storedMerklePathSeq.size, forgingBoxMerklePathInfoSeq.size)

    // Test 3: fifth epoch info - should request for the third epoch from storage
    val fifthEpoch = ConsensusEpochNumber @@ 5
    expectedEpochInfo = ConsensusEpochNumber @@ 3
    forgingBoxMerklePathInfoSeq = sidechainWallet.getForgingStakeMerklePathInfoOpt(fifthEpoch).get
    assertEquals("Merkle path seq size expected to be different.", storedMerklePathSeq.size, forgingBoxMerklePathInfoSeq.size)
  }

  @Test
  def testApplyConsensusEpochInfo(): Unit = {
    val mockedWalletBoxStorage: SidechainWalletBoxStorage = mock[SidechainWalletBoxStorage]
    val mockedSecretStorage: SidechainSecretStorage = mock[SidechainSecretStorage]
    val mockedWalletTransactionStorage: SidechainWalletTransactionStorage = mock[SidechainWalletTransactionStorage]
    val mockedForgingBoxesInfoStorage: ForgingBoxesInfoStorage = mock[ForgingBoxesInfoStorage]
    val mockedSidechainWalletCswDataProvider: SidechainWalletCswDataProvider = mock[SidechainWalletCswDataProvider]
    val mockedVersion: VersionTag = bytesToVersion(Array[Byte](32))
    val mockedApplicationWallet: ApplicationWallet = mock[ApplicationWallet]

    val sidechainWallet = new SidechainWallet(
      "seed".getBytes(StandardCharsets.UTF_8),
      mockedWalletBoxStorage,
      mockedSecretStorage,
      mockedWalletTransactionStorage,
      mockedForgingBoxesInfoStorage,
      mockedSidechainWalletCswDataProvider,
      params,
      mockedVersion,
      mockedApplicationWallet)



    val vrfPubKey1 = getVRFPublicKey(112233L)
    val vrfPubKey2 = getVRFPublicKey(445566L)
    val privateKey25519 = secretList.head.asInstanceOf[PrivateKey25519]
    // forger boxes: first two boxes must be aggregated to the single ForgingStakeInfo
    val forgerBoxes: Seq[ForgerBox] = Seq(
      getForgerBox(privateKey25519.publicImage(), 100L, 100L, privateKey25519.publicImage(), vrfPubKey1),
      getForgerBox(privateKey25519.publicImage(), 100L, 200L, privateKey25519.publicImage(), vrfPubKey1),
      getForgerBox(privateKey25519.publicImage(), 100L, 700L, privateKey25519.publicImage(), vrfPubKey2)
    )
    val forgingStakeInfo: Seq[ForgingStakeInfo] = ForgingStakeInfo.fromForgerBoxes(forgerBoxes)
      .sorted(Ordering[ForgingStakeInfo].reverse) // the sort is needed for test determinism only.

    val epochNumber: ConsensusEpochNumber = ConsensusEpochNumber @@ 3
    val merkleTree: MerkleTree = MerkleTree.createMerkleTree(forgingStakeInfo.map(_.hash).asJava)
    val forgersStake: Long = forgingStakeInfo.map(_.stakeAmount).sum

    val epochInfo: ConsensusEpochInfo = ConsensusEpochInfo(epochNumber, merkleTree, forgersStake)

    // Mock the list of delegated ForgerBoxes
    // Skip second item, so Wallet has no info about ForgingStakeInfo for the first two ForgerBoxes.
    Mockito.when(mockedForgingBoxesInfoStorage.getForgerBoxes)
      .thenReturn(Some(Seq(forgerBoxes.head, forgerBoxes.last)))

    // Verify epoch number and return predefined merklePathSeq
    Mockito.when(mockedForgingBoxesInfoStorage.updateForgingStakeMerklePathInfo(
      ArgumentMatchers.any[ConsensusEpochNumber](),
      ArgumentMatchers.any[Seq[ForgingStakeMerklePathInfo]]()))
      .thenAnswer(answer => {
        val epoch = answer.getArgument(0).asInstanceOf[ConsensusEpochNumber]
        val forgingStakeMerklePathInfoSeq = answer.getArgument(1).asInstanceOf[Seq[ForgingStakeMerklePathInfo]]

        assertEquals("Different epoch number request expected.", epochNumber, epoch)
        // We expect the forging stake merkle path data only for the second Forging stake,
        // because we have no info for the first Forging stake boxes in the Wallet
        assertEquals("Different merkle path seq size expected.", 1, forgingStakeMerklePathInfoSeq.size)
        val forgingStakeMerklePathInfo = forgingStakeMerklePathInfoSeq.head
        assertArrayEquals("Wrong merkle path applied.", merkleTree.rootHash(),
          forgingStakeMerklePathInfo.merklePath.apply(forgingStakeMerklePathInfo.forgingStakeInfo.hash))

        Success(mockedForgingBoxesInfoStorage)
      })

    sidechainWallet.applyConsensusEpochInfo(epochInfo)
  }
}
