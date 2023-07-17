package io.horizen.utxo.integration

import io.horizen.account.fork.ConsensusParamsFork
import io.horizen.chain.SidechainBlockInfo
import io.horizen.utxo.companion.SidechainTransactionsCompanion
import io.horizen.consensus.{ConsensusDataStorage, ConsensusParamsUtil, NonceConsensusEpochInfo, StakeConsensusEpochInfo}
import io.horizen.fixtures._
import io.horizen.fork.{ForkManagerUtil, SimpleForkConfigurator}
import io.horizen.history.AbstractHistory
import io.horizen.history.validation.{InvalidSidechainBlockDataException, SidechainBlockSemanticValidator}
import io.horizen.params.{MainNetParams, NetworkParams}
import io.horizen.storage.{InMemoryStorageAdapter, Storage}
import io.horizen.transaction.TransactionSerializer
import io.horizen.utils._
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.customtypes.SemanticallyInvalidTransactionSerializer
import io.horizen.utxo.storage.SidechainHistoryStorage
import io.horizen.{SidechainSettings, SidechainSyncInfo, SidechainTypes}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.{Before, Test}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.core.consensus.History.ProgressInfo
import sparkz.core.consensus.{History, ModifierSemanticValidity}
import sparkz.core.settings.SparkzSettings
import sparkz.util.{SparkzEncoding, idToBytes}

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import scala.util.{Failure, Success}

class SidechainHistoryTest extends JUnitSuite
  with MockitoSugar
  with SidechainBlockFixture
  with SidechainBlockInfoFixture
  with StoreFixture
  with CompanionsFixture
  with SparkzEncoding {

  var customTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]] = new JHashMap()
  customTransactionSerializers.put(11.toByte, SemanticallyInvalidTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCBT]])
  val sidechainTransactionsCompanion: SidechainTransactionsCompanion = getTransactionsCompanionWithCustomTransactions(customTransactionSerializers)

  var customAccountTransactionSerializers: JHashMap[JByte, TransactionSerializer[SidechainTypes#SCAT]] = new JHashMap()
  customAccountTransactionSerializers.put(11.toByte, SemanticallyInvalidTransactionSerializer.getSerializer.asInstanceOf[TransactionSerializer[SidechainTypes#SCAT]])

  val genesisBlock: SidechainBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, timestampOpt = Some(100000))
  var genesisBlockInfo: SidechainBlockInfo = _
  var params: NetworkParams = _

  val sidechainSettings: SidechainSettings = mock[SidechainSettings]
  val sparkzSettings: SparkzSettings = mock[SparkzSettings]
  var storage: Storage = _
  ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
    (0, ConsensusParamsFork.DefaultConsensusParamsFork)
  ))

  @Before
  def setUp(): Unit = {
    ForkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")
    // declare real genesis block id
    params = MainNetParams(new Array[Byte](32), genesisBlock.id, sidechainGenesisBlockTimestamp = 720 * 120)

    genesisBlockInfo = AbstractHistory.calculateGenesisBlockInfo(genesisBlock, params).copy(semanticValidity = ModifierSemanticValidity.Valid)
    Mockito.when(sidechainSettings.sparkzSettings)
      .thenAnswer(answer => {
        sparkzSettings
      })
    Mockito.when(sparkzSettings.dataDir) // NOTE: each call returns different dir path
      .thenAnswer(answer => {
        tempDir()
      })
  }

  @Test
  def genesisTest(): Unit = {
    val sidechainHistoryStorage = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    val consensusDataStorage = new ConsensusDataStorage(getStorage())
    genesisBlock.semanticValidity(params)
    val historyTry =
      SidechainHistory.createGenesisHistory(sidechainHistoryStorage, consensusDataStorage, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))
    assertTrue("Genesis history creation expected to be successful. ", historyTry.isSuccess)

    val history = historyTry.get
    assertFalse("Expected to be not empty.", history.isEmpty)
    assertEquals("Expected to have a genesis block height.", 1 , history.height)
    assertEquals("Expected to have a genesis block.", genesisBlock.id , history.bestBlockId)
    assertEquals("Expected to have a genesis block info.", genesisBlockInfo , history.bestBlockInfo)
    assertTrue("Expected to contain the genesis block.", history.contains(genesisBlock.id))
    assertTrue("Check for genesis block was failed.", history.isGenesisBlock(genesisBlock.id))
  }

  @Test
  def appendTest(): Unit = {
    val sidechainHistoryStorage = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    val consensusDataStorage = new ConsensusDataStorage(getStorage())
    val historyTry = SidechainHistory.createGenesisHistory(sidechainHistoryStorage, consensusDataStorage, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))
    assertTrue("Genesis history creation expected to be successful. ", historyTry.isSuccess)

    var history: SidechainHistory = historyTry.get
    var progressInfo: ProgressInfo[SidechainBlock] = null

    // Test 1: append block after genesis
    val blockB2: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, basicSeed = 111L)
    history.append(blockB2) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }

    // check
    assertEquals("Expected to have NOT updated height, best block was NOT changed.", 1 , history.height)
    assertEquals("Expected to have a genesis block, best block was NOT changed.", genesisBlock.id , history.bestBlockId)
    assertTrue("Block expected to be present.", history.contains(blockB2.id))
    assertEquals("Different progress info expected.", ProgressInfo[SidechainBlock](None, Seq(), Seq(blockB2)), progressInfo)

    // notify history that appended block is valid
    history = history.reportModifierIsValid(blockB2).get

    // check
    assertEquals("Expected to have updated height, best block was changed.", 2 , history.height)
    assertEquals("Expected to have different best block, best block was changed.", blockB2.id , history.bestBlockId)
    assertEquals("Expected to have different best block info, best block was changed.",
      SidechainBlockInfo(2, 2, blockB2.parentId, genesisBlock.timestamp + blockGenerationDelta * 1, ModifierSemanticValidity.Valid,  Seq(),  Seq(), WithdrawalEpochInfo(0, 1), history.getVrfOutput(blockB2.header, history.getOrCalculateNonceConsensusEpochInfo(blockB2.header.timestamp, blockB2.header.parentId)), genesisBlock.id), history.bestBlockInfo)


    // Test 2: append block after current tip (not after genesis)
    val blockB3: SidechainBlock = generateNextSidechainBlock(blockB2, sidechainTransactionsCompanion, params, basicSeed = 112L)
    history.append(blockB3) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }

    // check
    assertEquals("Expected to have NOT updated height, best block was NOT changed.", 2 , history.height)
    assertTrue("Block expected to be present.", history.contains(blockB3.id))
    assertEquals("Different progress info expected.", ProgressInfo[SidechainBlock](None, Seq(), Seq(blockB3)), progressInfo)

    // notify history that appended block is valid
    history = history.reportModifierIsValid(blockB3).get

    // check
    assertEquals("Expected to have updated height, best block was changed.", 3 , history.height)
    assertEquals("Expected to have different best block, best block was changed.", blockB3.id , history.bestBlockId)
    assertEquals("Expected to have different best block info, best block was changed.",
      SidechainBlockInfo(3, 3, blockB3.parentId, genesisBlock.timestamp + blockGenerationDelta * 2, ModifierSemanticValidity.Valid, Seq(),  Seq(), WithdrawalEpochInfo(0, 1), history.getVrfOutput(blockB3.header, history.getOrCalculateNonceConsensusEpochInfo(blockB3.header.timestamp, blockB3.header.parentId)), genesisBlock.id), history.bestBlockInfo)


    // At the moment we have an active chain G1 -> B2 -> B3,
    // where G1 - genesis with height 1,
    // B<N> - active chain block with height N,
    // b<N> - non active chain block with height N


    // Test 3: try to append block that parent is absent
    val unknownBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 444L)
    history.append(unknownBlock) match {
      case Success((_, _)) =>
        assertFalse("Exception expected during appending a block without parent present in History.", true)
      case Failure(e) =>
        e match {
          case _: IllegalArgumentException =>
          case _ => assertFalse("IllegalArgumentException expected during appending a block without parent present in History: %s".format(e.getMessage), true)
        }
    }

    assertFalse("Block expected to be NOT present.", history.contains(unknownBlock.id))


    // Test 4: try to append semantically invalid block
    val invalidBlock = createSemanticallyInvalidClone(generateNextSidechainBlock(blockB3, sidechainTransactionsCompanion, params, basicSeed = 1231L), sidechainTransactionsCompanion)
    history.append(invalidBlock) match {
      case Success(_) =>
        assertTrue("Exception expected during block appending", false)
      case Failure(_) => // expected behaviour
    }

    assertFalse("Block expected to be NOT present.", history.contains(invalidBlock.id))


    // Test 5: try to add block b2
    val forkBlockb2: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, basicSeed = 4441L)
    history.append(forkBlockb2) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }

    // check
    assertEquals("Expected to have NOT updated height, best block was NOT changed.", 3, history.height)
    assertTrue("Block expected to be present.", history.contains(forkBlockb2.id))
    assertEquals("Different progress info expected.", ProgressInfo[SidechainBlock](None, Seq(), Seq()), progressInfo)


    // Test 6: try to add block b3
    // score of b3 chain expected to be the same as B3 chain. So no chain switch expected.
    val forkBlockb3: SidechainBlock = generateNextSidechainBlock(forkBlockb2, sidechainTransactionsCompanion, params, basicSeed = 199L)
    history.append(forkBlockb3) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }

    // check
    assertEquals("Expected to have NOT updated height, best block was NOT changed.", 3, history.height)
    assertTrue("Block expected to be present.", history.contains(forkBlockb3.id))
    assertEquals("Different progress info expected.", ProgressInfo[SidechainBlock](None, Seq(), Seq()), progressInfo)


    /* At the moment we have a blockchain:
           G1
          / \
         B2   b2
        /     \
       B3       b3
    */


    // Test 7: try to add block b4. Chain switch to b4 fork expected.
    val forkBlockb4: SidechainBlock = generateNextSidechainBlock(forkBlockb3, sidechainTransactionsCompanion, params, basicSeed = 11192L)
    history.append(forkBlockb4) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }

    // check
    assertEquals("Expected to have NOT updated height, best block was NOT changed.", 3, history.height)
    assertTrue("Block expected to be present.", history.contains(forkBlockb4.id))
    assertTrue("Progress info chain switch expected.", progressInfo.branchPoint.isDefined)
    assertEquals("Different progress info branch point expected.", genesisBlock.id, progressInfo.branchPoint.get)
    assertEquals("Different progress info remove data expected", Seq(blockB2, blockB3), progressInfo.toRemove)
    assertEquals("Different progress info apply data expected", Seq(forkBlockb2, forkBlockb3, forkBlockb4), progressInfo.toApply)


    // notify history that appended block is valid
    history = history.reportModifierIsValid(forkBlockb2).get
    history = history.reportModifierIsValid(forkBlockb3).get
    history = history.reportModifierIsValid(forkBlockb4).get

    // check
    assertEquals("Expected to have updated height, best block was changed.", 4, history.height)
    assertEquals("Expected to have different best block, best block was changed.", forkBlockb4.id , history.bestBlockId)


    // Test 8: try to add block B4TxSemInvalid, that contains invalid transactions
    val blockB4TxSemInvalid: SidechainBlock = generateNextSidechainBlockWithInvalidTransaction(blockB3, sidechainTransactionsCompanion, params, basicSeed = 888L)
    history.append(blockB4TxSemInvalid) match {
      case Success((_, _)) =>
        assertFalse("Exception expected during appending a block with semantically invalid transaction.", true)
      case Failure(e) =>
        e match {
          case _: InvalidSidechainBlockDataException =>
          case _ => assertFalse("InvalidSidechainBlockDataException expected during appending a block with semantically invalid transaction: %s".format(e.getMessage), true)
        }
    }


    // Test 9: try to add block B4, that contains semantically valid transaction, but invalid from the State view.
    val blockB4: SidechainBlock = generateNextSidechainBlock(blockB3, sidechainTransactionsCompanion, params, basicSeed = 888L)
    history.append(blockB4) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }

    // check
    assertEquals("Expected to have NOT updated height, best block was NOT changed.", 4, history.height)
    assertTrue("Block expected to be present.", history.contains(blockB4.id))
    assertEquals("Block expected to have undefined semantic validity.", ModifierSemanticValidity.Unknown, history.isSemanticallyValid(blockB4.id))
    assertEquals("Different progress info expected.", ProgressInfo[SidechainBlock](None, Seq(), Seq()), progressInfo)


    // notify history that appended block is invalid
    history.reportModifierIsInvalid(blockB4, progressInfo) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
    }

    // check
    assertEquals("Expected to have updated height, best block was changed.", 4, history.height)
    assertEquals("Expected to have different best block, best block was changed.", forkBlockb4.id , history.bestBlockId)
    assertEquals("Block expected to have undefined semantic validity.", ModifierSemanticValidity.Invalid, history.isSemanticallyValid(blockB4.id))


    /* At the moment we have a blockchain:
           G1
          / \
         B2   b2
        /     \
       B3       b3
      /          \
     B4(invalid)  b4 (active chain tip)
    */


    // Test 10: try to add block B5, to switch back from "b"-chain to "B"-chain
    // Because of B4 is invalid, no switch expected.
    val blockB5: SidechainBlock = generateNextSidechainBlock(blockB4, sidechainTransactionsCompanion, params)
    history.append(blockB5) match {
      case Success((hist, prog)) =>
        history = hist
        progressInfo = prog
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }

    // check
    assertEquals("Expected to have NOT updated height, best block was NOT changed.", 4, history.height)
    assertTrue("Block expected to be present.", history.contains(blockB5.id))
    assertEquals("Block expected to have undefined semantic validity.", ModifierSemanticValidity.Unknown, history.isSemanticallyValid(blockB5.id))
    assertEquals("Different progress info expected.", ProgressInfo[SidechainBlock](None, Seq(), Seq()), progressInfo)
  }

  @Test
  def bestForkChangesTest(): Unit = {
    val sidechainHistoryStorage = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    // Init chain with 10 blocks
    val consensusDataStorage = new ConsensusDataStorage(getStorage())
    val historyTry = SidechainHistory.createGenesisHistory(sidechainHistoryStorage, consensusDataStorage, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))
    assertTrue("Genesis history creation expected to be successful. ", historyTry.isSuccess)

    var history = historyTry.get
    var blockSeq = Seq[SidechainBlock](genesisBlock)
    var blocksToAppend = 9

    while(blocksToAppend > 0) {
      val block = generateNextSidechainBlock(blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 86766L)

      history.append(block) match {
        case Success((hist, _)) =>
          history = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      // notify history that appended block is valid
      history = history.reportModifierIsValid(block).get

      blockSeq = blockSeq :+ block
      blocksToAppend -= 1
    }

    assertEquals("Expected to have different height", 10, history.height)


    // Test 1: fork changes for block after genesis
    val blockH2 = generateNextSidechainBlock(blockSeq.head, sidechainTransactionsCompanion, params, basicSeed = 9000L)
    history.bestForkChanges(blockH2) match {
      case Success(progressInfo) =>
        assertEquals("Different progress info branch point expected.", blockSeq.head.id, progressInfo.branchPoint.get)
        assertEquals("Different progress info remove data expected", blockSeq.tail, progressInfo.toRemove)
        assertEquals("Different progress info apply data expected", Seq(blockH2), progressInfo.toApply)
      case Failure(e) => assertFalse("Unexpected Exception occurred during bestForkChanges calculation: %s".format(e.getMessage), true)
    }


    // Test 2: fork changes for block in the middle
    val blockH5 = generateNextSidechainBlock(blockSeq(4), sidechainTransactionsCompanion, params, basicSeed = 65522L)
    history.bestForkChanges(blockH5) match {
      case Success(progressInfo) =>
        assertEquals("Different progress info branch point expected.", blockSeq(4).id, progressInfo.branchPoint.get)
        assertEquals("Different progress info remove data expected", blockSeq.takeRight(5), progressInfo.toRemove)
        assertEquals("Different progress info apply data expected", Seq(blockH5), progressInfo.toApply)
      case Failure(e) => assertFalse("Unexpected Exception occurred during bestForkChanges calculation: %s".format(e.getMessage), true)
    }


    // Test 3: fork changes for block before last
    val blockH9 = generateNextSidechainBlock(blockSeq(8), sidechainTransactionsCompanion, params, basicSeed = 1231234L)
    history.bestForkChanges(blockH9) match {
      case Success(progressInfo) =>
        assertEquals("Different progress info branch point expected.", blockSeq(8).id, progressInfo.branchPoint.get)
        assertEquals("Different progress info remove data expected", Seq(blockSeq.last), progressInfo.toRemove)
        assertEquals("Different progress info apply data expected", Seq(blockH9), progressInfo.toApply)
      case Failure(e) => assertFalse("Unexpected Exception occurred during bestForkChanges calculation: %s".format(e.getMessage), true)
    }


    // Test 4: fork changes for block after last one
    // wrong situation
    /*
     * There is the case when we are just applying a valid block whose id
     * had been rollbacked from state, for instance after an ungraceful node shutdown while updating the storage
     * In that case the recovery procedure at node startup hits this situation and we have a valid progressInfo with no blocks to remove
     */
      val blockH11 = generateNextSidechainBlock(blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 5545454L)
      history.bestForkChanges(blockH11) match {
        case Success(progressInfo) =>
          assertTrue("No blocks to remove expected after bestForkChanges calculation", progressInfo.toRemove.isEmpty)
          assertTrue("Some blocks to be applied expected after bestForkChanges calculation", progressInfo.toApply.nonEmpty)
        case Failure(_) =>
          assertTrue("Exception not expected during bestForkChanges calculation", false)
      }



    // Test 5: fork changes for block, which parent doesn't exist
    // wrong situation
    val unknownBlock = SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 444L)
    history.bestForkChanges(unknownBlock) match {
      case Success(progressInfo) =>
        assertTrue("Exception expected during bestForkChanges calculation", false)
      case Failure(_) =>
    }
  }

  @Test
  def applicableTryTest(): Unit = {
    val sidechainHistoryStorage = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    val consensusDataStorage = new ConsensusDataStorage(getStorage())
    val historyTry = SidechainHistory.createGenesisHistory(sidechainHistoryStorage, consensusDataStorage, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))
    assertTrue("Genesis history creation expected to be successful. ", historyTry.isSuccess)

    var history: SidechainHistory = historyTry.get

    val applicableBlock: SidechainBlock = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, params, basicSeed = 5656521L)
    history.applicableTry(applicableBlock) match {
      case Success(_) =>
      case Failure(_) =>
        assertFalse("Block expected to be applicable", true)
    }

    val irrelevantBlock: SidechainBlock = generateNextSidechainBlock(SidechainBlockFixture.generateSidechainBlock(sidechainTransactionsCompanion, basicSeed = 99L), sidechainTransactionsCompanion, params, basicSeed = 7872832L)
    history.applicableTry(irrelevantBlock) match {
      case Success(_) =>
        assertTrue("Exception expected on applicableTry for block without parent already stored in history.", false)
      case Failure(_) =>
    }

  }

  @Test
  def synchronizationTest(): Unit = {
    val sidechainHistoryStorage1 = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    // Create first history object
    val consensusDataStorage1 = new ConsensusDataStorage(getStorage())
    val history1Try = SidechainHistory.createGenesisHistory(sidechainHistoryStorage1, consensusDataStorage1, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))

    assertTrue("Genesis history1 creation expected to be successful. ", history1Try.isSuccess)
    var history1: SidechainHistory = history1Try.get
    // Init history1 with 19 more blocks
    var history1blockSeq = Seq[SidechainBlock](genesisBlock)
    var blocksToAppend = 19
    while(blocksToAppend > 0) {
      val block = generateNextSidechainBlock(history1blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 443356L)
      history1.append(block) match {
        case Success((hist, _)) =>
          history1 = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      // notify history that appended block is valid
      history1 = history1.reportModifierIsValid(block).get

      history1blockSeq = history1blockSeq :+ block
      blocksToAppend -= 1
    }
    assertEquals("Expected to have different height", 20, history1.height)

    val sidechainHistoryStorage2 = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    val consensusDataStorage2 = new ConsensusDataStorage(getStorage())

    // Create second history object
    val history2Try = SidechainHistory.createGenesisHistory(sidechainHistoryStorage2, consensusDataStorage2, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))
    assertTrue("Genesis history2 creation expected to be successful. ", history2Try.isSuccess)
    var history2: SidechainHistory = history2Try.get
    // Init history2 with 18 more blocks
    var history2blockSeq = history1blockSeq.take(19)
    for(block <- history2blockSeq.tail) { // without genesis
      history2.append(block) match {
        case Success((hist, _)) =>
          history2 = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      // notify history that appended block is valid
      history2 = history2.reportModifierIsValid(block).get
    }
    assertEquals("Expected to have different height", 19, history2.height)


    // Test 1: retrieve history1 sync info and check against history2
    // get history1 syncInfo
    var history1SyncInfo: SidechainSyncInfo = history1.syncInfo
    assertTrue("History 1 sync info expected to be not empty", history1SyncInfo.knownBlockIds.nonEmpty)
    assertEquals("History 1 sync info starting point expected to be best block of its chain", history1blockSeq.last.id, history1SyncInfo.startingPoints.head._2)

    // Compare history1 syncInfo with history2
    var comparisonResult: History.HistoryComparisonResult = history2.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to be older then history 2 chain", History.Older, comparisonResult)
    // Verify history2 continuationIds for history1 info
    var continuationIds = history2.continuationIds(history1SyncInfo, Int.MaxValue -1)
    assertTrue("History 2 continuation Ids for history 1 info expected to be empty.", continuationIds.isEmpty)


    // Test 2: check history1 sync info against itself
    comparisonResult = history1.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to equal to itself", History.Equal, comparisonResult)
    // Verify history1 continuationIds for its info
    continuationIds = history1.continuationIds(history1SyncInfo, Int.MaxValue -1)
    assertTrue("History 1 continuation Ids for itself info expected to be empty.", continuationIds.isEmpty)


    // Test 3: retrieve history2 sync info and check against history1
    // get history2 syncInfo
    var history2SyncInfo: SidechainSyncInfo = history2.syncInfo
    assertTrue("History 2 sync info expected to be not empty", history2SyncInfo.knownBlockIds.nonEmpty)
    assertEquals("History 2 sync info starting point expected to be best block of its chain", history2blockSeq.last.id, history2SyncInfo.startingPoints.head._2)

    // Compare history2 syncInfo with history1
    comparisonResult = history1.compare(history2SyncInfo)
    assertEquals("History 2 chain expected to be younger then history 1 chain", History.Younger, comparisonResult)
    // Verify history1 continuationIds for history2 info
    continuationIds = history1.continuationIds(history2SyncInfo, Int.MaxValue -1)
    assertTrue("History 1 continuation Ids for history 2 info expected to be defined.", continuationIds.nonEmpty)
    assertEquals("History 1 continuation Ids for history 2 info expected to be with given size empty.", 1, continuationIds.size)
    assertEquals("History 1 continuation Ids for history 2 should contain different data.", history1blockSeq.last.id, continuationIds.head._2)



    // Append to history2 one more block, different to the one in history1
    val forkBlock = generateNextSidechainBlock(history2blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 77655L)
    history2blockSeq = history2blockSeq :+ forkBlock
    history2.append(forkBlock) match {
      case Success((hist, _)) =>
        history2 = hist
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }
    // notify history that appended block is valid
    history2 = history2.reportModifierIsValid(forkBlock).get


    // Test 4: compare history1 syncInfo with history2, they have fork on lasts block, height is the same.
    comparisonResult = history2.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to be a fork related to history 2 chain", History.Fork, comparisonResult)
    // Verify history2 continuationIds for history1 info
    continuationIds = history2.continuationIds(history1SyncInfo, Int.MaxValue -1)
    assertEquals("History 1 continuation Ids for history 2 info expected to be with given size empty.", 1, continuationIds.size)
    assertEquals("History 1 continuation Ids for history 2 should contain different data.", history2blockSeq.last.id, continuationIds.head._2)


    // Test 5: Append history1.bestblock to history2, but don't make it best.
    // compare history1 syncInfo with history2, they have fork on lasts block, height is the same.
    history2.append(history1blockSeq.last) match {
      case Success((hist, _)) =>
        history2 = hist
      case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
    }
    comparisonResult = history2.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to be a fork related to history 2 chain", History.Fork, comparisonResult)
    // Verify history2 continuationIds for history1 info
    continuationIds = history2.continuationIds(history1SyncInfo, Int.MaxValue -1)
    assertEquals("History 1 continuation Ids for history 2 info expected to be with given size empty.", 1, continuationIds.size)
    assertEquals("History 1 continuation Ids for history 2 should contain different data.", history2blockSeq.last.id, continuationIds.head._2)
  }

  @Test
  def synchronizationTestWithHighBlockNumber(): Unit = {
    // -----------------------------------------------------------------------------------------------------------------
    // Test 1:
    // Create first history object
    val sidechainHistoryStorage1 = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    val consensusDataStorage1 = new ConsensusDataStorage(getStorage())
    val history1Try = SidechainHistory.createGenesisHistory(sidechainHistoryStorage1, consensusDataStorage1, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))
    var history1: SidechainHistory = history1Try.get

    // Init history1 with 299 more blocks
    var history1blockSeq = Seq[SidechainBlock](genesisBlock)
    var blocksToAppend = 299
    while (blocksToAppend > 0) {
      val block = generateNextSidechainBlock(history1blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 443356L)
      history1.append(block) match {
        case Success((hist, _)) =>
          history1 = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      history1 = history1.reportModifierIsValid(block).get
      history1blockSeq = history1blockSeq :+ block
      blocksToAppend -= 1
    }
    assertEquals("Expected to have different height", 300, history1.height)

    // Create second history object
    val sidechainHistoryStorage2 = new SidechainHistoryStorage(getStorage(), sidechainTransactionsCompanion, params)
    val consensusDataStorage2 = new ConsensusDataStorage(getStorage())
    val history2Try = SidechainHistory.createGenesisHistory(sidechainHistoryStorage2, consensusDataStorage2, params, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L))
    assertTrue("Genesis history2 creation expected to be successful. ", history2Try.isSuccess)
    var history2: SidechainHistory = history2Try.get

    // Init history2 with 150 blocks taken from history1
    var history2blockSeq = history1blockSeq.take(150)
    for (block <- history2blockSeq.tail) { // without genesis
      history2.append(block) match {
        case Success((hist, _)) =>
          history2 = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      history2 = history2.reportModifierIsValid(block).get
    }
    assertEquals("Expected to have different height", 150, history2.height)

    // -----------------------------------------------------------------------------------------------------------------
    // retrieve history1 sync info and check against history2, a Older status is expected
    var history1SyncInfo: SidechainSyncInfo = history1.syncInfo
    var comparisonResult: History.HistoryComparisonResult = history2.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to be older then history 2 chain", History.Older, comparisonResult)
    // retrieve history2 sync info and check against history1, a Younger status is expected
    var history2SyncInfo: SidechainSyncInfo = history2.syncInfo
    comparisonResult = history1.compare(history2SyncInfo)
    assertEquals("History 2 chain expected to be younger then history 1 chain", History.Younger, comparisonResult)

    // -----------------------------------------------------------------------------------------------------------------
    // Test 2:
    // update history2 with 10 more blocks (introduce a fork).
    // Here we can test a corner case in which the comparison between two histories will result in a Older state even if there is Fork
    // The node1 (at height 300) will send the node2 a list with the modifiers ID at the following height:
    // 300 - 299 - 298 - 297 - 296 - 295 - 294 - 293 - 292 - 291 - 289 - 285 - 277 - 261 - 229 - 165 - 37 - 1
    // The divergent suffix list will be the modifiers at the following height (size 17):
    // 37 - 165 - 229 - 261 - 277 - 285 - 289 - 291 - 292 - 293 - 294 - 295 - 296 - 297 - 298 - 299 - 300
    // The internal logic will calculate the second divergent suffix height.
    // The result (165) is greater than the current height (160) and the final result will be an Older status
    blocksToAppend = 10
    while (blocksToAppend > 0) {
      val block = generateNextSidechainBlock(history2blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 334456L)
      history2.append(block) match {
        case Success((hist, _)) =>
          history2 = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      history2 = history2.reportModifierIsValid(block).get
      history2blockSeq = history2blockSeq :+ block
      blocksToAppend -= 1
    }
    assertEquals("Expected to have different height", 160, history2.height)

    // -----------------------------------------------------------------------------------------------------------------
    // retrieve history1 sync info and check against history2, an Older status is expected even if we have a Fork
    history1SyncInfo = history1.syncInfo
    comparisonResult = history2.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to be a fork related to history 2 chain", History.Older, comparisonResult)
    // retrieve history2 sync info and check against history1, a Fork status is expected
    history2SyncInfo = history2.syncInfo
    comparisonResult = history1.compare(history2SyncInfo)
    assertEquals("History 2 chain expected to be a fork related to history 1 chain", History.Fork, comparisonResult)

    // -----------------------------------------------------------------------------------------------------------------
    // Test 3:
    // update history2 with 40 more blocks
    blocksToAppend = 40
    while (blocksToAppend > 0) {
      val block = generateNextSidechainBlock(history2blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 334456L)
      history2.append(block) match {
        case Success((hist, _)) =>
          history2 = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      history2 = history2.reportModifierIsValid(block).get
      history2blockSeq = history2blockSeq :+ block
      blocksToAppend -= 1
    }
    assertEquals("Expected to have different height", 200, history2.height)

    // -----------------------------------------------------------------------------------------------------------------
    // retrieve history1 sync info and check against history2, a Fork status is expected
    history1SyncInfo = history1.syncInfo
    comparisonResult = history2.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to be a fork related to history 2 chain", History.Fork, comparisonResult)
    // retrieve history2 sync info and check against history1, a Fork status is expected
    history2SyncInfo = history2.syncInfo
    comparisonResult = history1.compare(history2SyncInfo)
    assertEquals("History 2 chain expected to be a fork related to history 1 chain", History.Fork, comparisonResult)

    // -----------------------------------------------------------------------------------------------------------------
    // Test 4:
    // update history2 with 150 more blocks
    blocksToAppend = 150
    while (blocksToAppend > 0) {
      val block = generateNextSidechainBlock(history2blockSeq.last, sidechainTransactionsCompanion, params, basicSeed = 334456L)
      history2.append(block) match {
        case Success((hist, _)) =>
          history2 = hist
        case Failure(e) => assertFalse("Unexpected Exception occurred during block appending: %s".format(e.getMessage), true)
      }
      // notify history that appended block is valid
      history2 = history2.reportModifierIsValid(block).get
      history2blockSeq = history2blockSeq :+ block
      blocksToAppend -= 1
    }
    assertEquals("Expected to have different height", 350, history2.height)

    // -----------------------------------------------------------------------------------------------------------------
    // retrieve history1 sync info and check against history2, a Fork status is expected
    history1SyncInfo = history1.syncInfo
    comparisonResult = history2.compare(history1SyncInfo)
    assertEquals("History 1 chain expected to be a fork related to history 2 chain", History.Fork, comparisonResult)
    // retrieve history2 sync info and check against history1, a Fork status is expected
    history2SyncInfo = history2.syncInfo
    comparisonResult = history1.compare(history2SyncInfo)
    assertEquals("History 2 chain expected to be a fork related to history 1 chain", History.Fork, comparisonResult)
  }

  @Test
  def checkSidechainBlockInfoCreation(): Unit = {
    val firstBlockVrfOutputOpt = Option(VrfGenerator.generateVrfOutput(241))
    val secondBlockVrfOutputOpt = Option(VrfGenerator.generateVrfOutput(242))

    val testParams = MainNetParams(new Array[Byte](32),
      genesisBlock.id,
      sidechainGenesisBlockTimestamp = 100000,
      consensusSecondsInSlot = 10,
      )
    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      (0, new ConsensusParamsFork(2))
    ))

    val sidechainHistoryStorage = new SidechainHistoryStorage(new InMemoryStorageAdapter(), sidechainTransactionsCompanion, params)
    // Create first history object
    val consensusDataStorage1 = new ConsensusDataStorage(new InMemoryStorageAdapter())
    var history = Mockito.spy(SidechainHistory.createGenesisHistory(sidechainHistoryStorage, consensusDataStorage1, testParams, genesisBlock, Seq(new SidechainBlockSemanticValidator[SidechainTypes#SCBT, SidechainBlock](params)), Seq(), StakeConsensusEpochInfo(idToBytes(genesisBlock.id), 0L)).get)
    Mockito.doAnswer(_ => firstBlockVrfOutputOpt).when(history).getVrfOutput(ArgumentMatchers.any[SidechainBlockHeader], ArgumentMatchers.any[NonceConsensusEpochInfo])

    val block1 = generateNextSidechainBlock(genesisBlock, sidechainTransactionsCompanion, testParams)
    assertEquals(2, TimeToEpochUtils.timeStampToEpochNumber(testParams, block1.timestamp))

    history = history.append(block1).get._1
    history = history.reportModifierIsValid(block1).get
    val block1Info = history.bestBlockInfo
    assertEquals(genesisBlock.id, block1Info.lastBlockInPreviousConsensusEpoch)
    assertEquals(history.blockToBlockInfo(block1).get.copy(semanticValidity = ModifierSemanticValidity.Valid, vrfOutputOpt = firstBlockVrfOutputOpt), block1Info) //Sidechain block Info creation doesn't fill semantic validity

    val block2 = generateNextSidechainBlock(block1, sidechainTransactionsCompanion, testParams)
    history = Mockito.spy(history)
    Mockito.doAnswer(_ => secondBlockVrfOutputOpt).when(history).getVrfOutput(ArgumentMatchers.any[SidechainBlockHeader], ArgumentMatchers.any[NonceConsensusEpochInfo])
    history = history.append(block2).get._1
    history = history.reportModifierIsValid(block2).get
    val block2Info = history.bestBlockInfo
    assertEquals(genesisBlock.id, block2Info.lastBlockInPreviousConsensusEpoch)

    val block3 = generateNextSidechainBlock(block2, sidechainTransactionsCompanion, testParams)
    assertEquals(3, TimeToEpochUtils.timeStampToEpochNumber(testParams, block3.timestamp))
    history = history.append(block3).get._1
    history = history.reportModifierIsValid(block3).get
    val block3Info = history.bestBlockInfo
    assertEquals(block2.id, block3Info.lastBlockInPreviousConsensusEpoch)
  }
}
