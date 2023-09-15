package io.horizen.consensus


import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}
import io.horizen.chain.SidechainBlockInfo
import io.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import io.horizen.fork.{ConsensusParamsFork, ConsensusParamsForkInfo, CustomForkConfiguratorWithConsensusParamsFork, ForkManagerUtil}
import io.horizen.params.{NetworkParams, NetworkParamsUtils, TestNetParams}
import io.horizen.proof.VrfProof
import io.horizen.storage.{InMemoryStorageAdapter, SidechainBlockInfoProvider}
import io.horizen.utils
import io.horizen.utils.{BytesUtils, TimeToEpochUtils, Utils}
import io.horizen.vrf.VrfOutput
import org.junit.Assert._
import sparkz.core.consensus.ModifierSemanticValidity
import org.junit.{Before, Test}
import sparkz.util._

import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer


class TestedConsensusDataProvider(slotsPresentation: List[List[Int]],
                                  val params: NetworkParams)
  extends ConsensusDataProvider
  with NetworkParamsUtils
  with SparkzLogging {

  private val dummyWithdrawalEpochInfo = utils.WithdrawalEpochInfo(0, 0)

  val testVrfProofData: String = "bf4d2892d7562e973ba8a60ef5f9262c088811cc3180c3389b1cef3a66dcfb390d9bb91cebab11bcae871d6a6bd203292264d1002ac70b539f7025a9a813637e1866b2d5c289f28646385549bac7681ef659f2d1d8ca1a21037b036c7925b692e8"
  val testVrfOutputData: String = "c8fbb101cd3bd0fc7dc22133778529ce49ed94678a2c6532e3d6013efa91933f"

  val genesisVrfProof = new VrfProof(BytesUtils.fromHexString(testVrfProofData))
  val genesisVrfOutput = new VrfOutput(BytesUtils.fromHexString(testVrfOutputData))

  private val vrfData = slotsPresentationToVrfData(slotsPresentation)
  val blockIdAndInfosPerEpoch: Seq[Seq[(ModifierId, SidechainBlockInfo)]] =
    generateBlockIdsAndInfos(genesisVrfProof, genesisVrfOutput, vrfData)

  val epochIds: Seq[ConsensusEpochId] = blockIdAndInfosPerEpoch.map(epoch => blockIdToEpochId(epoch.last._1))


  val storage = new BlocksInfoProvider()
  blockIdAndInfosPerEpoch.flatten.foreach{case (id, info) => storage.addBlockInfo(id, info)}

  val consensusDataStorage = new ConsensusDataStorage(new InMemoryStorageAdapter())
  epochIds.zipWithIndex.foreach{case (epochId, index) =>
    consensusDataStorage.addStakeConsensusEpochInfo(epochId, StakeConsensusEpochInfo(epochId.getBytes(StandardCharsets.UTF_8).take(merkleTreeHashLen), (index + 1) * 1000))}

  //vrfData -- contains data per epoch for filling SidechainBlockInfo, in case if VrfData contains None it means that slot shall be skipped
  private def generateBlockIdsAndInfos(genesisVrfProof: VrfProof,
                                       genesisVrfOutput: VrfOutput,
                                       vrfData: List[List[Option[(VrfProof, VrfOutput)]]]): Seq[Seq[(ModifierId, SidechainBlockInfo)]] = {

    val parentOfGenesisBlock = bytesToId(Utils.doubleSHA256Hash("genesisParent".getBytes(StandardCharsets.UTF_8)))

    val genesisBlockInfo = new SidechainBlockInfo(0, 0, parentOfGenesisBlock, params.sidechainGenesisBlockTimestamp, ModifierSemanticValidity.Valid, Seq(), Seq(), dummyWithdrawalEpochInfo, None, params.sidechainGenesisBlockId)

    val genesisSidechainBlockIdAndInfo = (params.sidechainGenesisBlockId, genesisBlockInfo)
    val accumulator =
      ListBuffer[Seq[(ModifierId, SidechainBlockInfo)]](ListBuffer(genesisSidechainBlockIdAndInfo))

    vrfData.zipWithIndex.foldLeft(accumulator) { case (acc, (processed, index)) =>
      val previousId: ModifierId = acc.last.last._1
      val nextTimeStamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params.sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(index + 2), intToConsensusSlotNumber(1))
      val newData =
        generateBlockIdsAndInfosIter(previousId, nextTimeStamp, previousId, ListBuffer[(ModifierId, SidechainBlockInfo)](), processed)
      acc.append(newData)
      acc
    }
  }

  @tailrec
  final def generateBlockIdsAndInfosIter(previousId: ModifierId,
                                         nextTimestamp: Long,
                                         lastBlockInPreviousConsensusEpoch: ModifierId,
                                         acc: ListBuffer[(ModifierId, SidechainBlockInfo)],
                                         vrfData: List[Option[(VrfProof, VrfOutput)]]): Seq[(ModifierId, SidechainBlockInfo)] = {
    val seqTimestampsForActivation = ConsensusParamsUtil.getConsensusParamsForkTimestampActivation()
    var secondsInSlot = ConsensusParamsUtil.getConsensusParamsForkActivation.head.consensusParamsFork.consensusSecondsInSlot
//    if (nextTimestamp > seqTimestampsForActivation(1))
//      secondsInSlot = ConsensusParamsUtil.getConsensusParamsForkActivation(1).consensusParamsFork.consensusSecondsInSlot
    vrfData.headOption match {
      case Some(Some((vrfProof, vrfOutput))) => {
        val idInfo = generateSidechainBlockInfo(previousId, nextTimestamp, vrfProof, vrfOutput, lastBlockInPreviousConsensusEpoch)
        acc += idInfo
        generateBlockIdsAndInfosIter(idInfo._1, nextTimestamp + secondsInSlot, lastBlockInPreviousConsensusEpoch, acc, vrfData.tail)
      }
      case Some(None) => generateBlockIdsAndInfosIter(previousId, nextTimestamp + secondsInSlot, lastBlockInPreviousConsensusEpoch, acc, vrfData.tail)
      case None => acc
    }
  }

  private def generateSidechainBlockInfo(parentId: ModifierId, timestamp: Long, vrfProof: VrfProof, vrfOutput: VrfOutput, lastBlockInPreviousConsensusEpoch: ModifierId): (ModifierId, SidechainBlockInfo) = {
    val newBlockId = bytesToId(Utils.doubleSHA256Hash(parentId.getBytes(StandardCharsets.UTF_8)))
    val blockInfo =
      new SidechainBlockInfo(0, 0, parentId, timestamp, ModifierSemanticValidity.Valid, Seq(), Seq(), dummyWithdrawalEpochInfo, Option(vrfOutput), lastBlockInPreviousConsensusEpoch)

    (newBlockId, blockInfo)
  }


  private def slotsPresentationToVrfData(slotsRepresentations: List[List[Int]], prefix: String = ""):  List[List[Option[(VrfProof, VrfOutput)]]] = {
    slotsRepresentations.zipWithIndex.map{case (slotsRepresentationsForEpoch, epochIndex) =>
      slotsRepresentationsForEpoch.zipWithIndex.map{
        case (1, slotIndex) =>
          Some(new VrfProof(BytesUtils.fromHexString(testVrfProofData)),
            new VrfOutput(BytesUtils.fromHexString(testVrfOutputData)))
        case (0, _) =>
          None
        case _ => throw new IllegalArgumentException
      }
    }
  }

  def getConsensusEpochInfoForBlock(idInfo: (ModifierId, SidechainBlockInfo)): FullConsensusEpochInfo = {
    getFullConsensusEpochInfoForBlock(idInfo._2.timestamp, idInfo._2.parentId)
  }

  def getInfoForCheckingBlockInEpochNumber(epochNumber: Int): FullConsensusEpochInfo = {
    getConsensusEpochInfoForBlock(blockIdAndInfosPerEpoch(epochNumber - 1).head)
  }
}

class BlocksInfoProvider extends SidechainBlockInfoProvider {
  private val storage = mutable.Map[ModifierId, SidechainBlockInfo]()

  override def blockInfoById(blockId: ModifierId): SidechainBlockInfo = storage(blockId)
  def addBlockInfo(blockId: ModifierId, sidechainBlockInfo: SidechainBlockInfo): Unit = storage.put(blockId, sidechainBlockInfo)
}
/*
* "This class test the correctness of the stake root and nonce calculation between the various epochs.
* The PR developments introduce only the fixes needed to manage the new method signatures of the Consensus.
*
* We need to introduce a change in the consensus params during the 10 epoch tested,
* something that will also change the slot number for every epoch. Investigate this point and its implementation."
* */
class ConsensusDataProviderTest extends CompanionsFixture{
  val generator: SidechainBlockFixture = new SidechainBlockFixture {}
  val dummyWithdrawalEpochInfo = utils.WithdrawalEpochInfo(0, 0)
  val slotsInEpoch = 10  //eligible range 4-6
  val secondsInSlot = 100

  val slotsInEpoch2 = 12  //eligible range 5-7
  val secondsInSlot2 = 100
  val startFork2 = 5

  val slotsInEpoch3 = 10
  val secondsInSlot3 = 100

  @Before
  def init(): Unit = {
    ForkManagerUtil.initializeForkManager(CustomForkConfiguratorWithConsensusParamsFork.getCustomForkConfiguratorWithConsensusParamsFork(Seq(0,startFork2), Seq(slotsInEpoch, slotsInEpoch2), Seq(secondsInSlot, secondsInSlot2)), "regtest")
  }

  // epoch 7 impacts nonce epoch 8
  // nonce changes when non-quiet slots change (1 to 0)
  // stake changes when last block id changes in epochs (e.g 6 7 8) 1, 0, 0  to 0, 1, 1
  @Test
  def test(): Unit = {
    val slotsPresentationForFirstDataProvider: List[List[Int]] = List(
//   1 -- block in slot is present; 0 -- no block for slot
//   slots 1  2  3  4  5  6  7  8  9  10
//      List(1, 1, 1, 1, 1, 1, 1, 1, 1, 1), //2 epoch
//      List(1, 1, 0, 0, 1, 1, 1, 1, 0, 0), //3 epoch
//      List(1, 1, 0, 0, 1, 1, 1, 1, 0, 0), //4 epoch
//      List(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1), //5 epoch
//
//      List(1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1), //6 epoch
//      List(1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 0, 0, 0, 0), //7 epoch
//      List(1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0,   0, 0, 1, 0, 0), //8 epoch
//
//      List(0, 1, 1, 0, 1, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0,   0, 0, 0, 1, 0), //9 epoch
//      List(0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 1,   0, 0, 0, 0, 1), //10 epoch
      //eligible slot start 4
      //eligible slot end 6

      List(1, 1, 1, 1, 1, 1, 1, 1, 1, 1), //2 epoch
      List(1, 1, 0, 0, 1, 1, 1, 1, 0, 0), //3 epoch
      List(1, 1, 0, 0, 1, 1, 1, 1, 0, 0), //4 epoch
      List(0, 0, 0, 0, 1, 1, 1, 1, 0, 0,0,0), //5 epoch

      List(1, 0, 0, 0, 0, 0, 0, 0, 0, 1,0,0), //6 epoch
      List(1, 0, 1, 0, 1, 0, 1, 0, 1, 1,0,0), //7 epoch
      List(0, 0, 1, 0, 1, 1, 1, 0, 1, 1,   0, 0), //8 epoch

      List(0, 1, 1, 0, 1, 0, 1, 1, 1, 1,   0, 0), //9 epoch
      List(0, 1, 1, 0, 1, 0, 1, 1, 0, 1,   0, 0), //10 epoch

//quiet slots - oni koji nisu u rangu 4-6, a rangovi idu 1-10
      //non quiet - oni u 4-6
    )

    /*
      List(0, 1, 1, 0, 0, 0, 0, 1, 1, 0), //6 epoch, changed quiet slots compared to original
      List(0, 0, 1, 0, 1, 0, 1, 0, 1, 1), //7 epoch, changed quiet slots
      List(0, 0, 0, 0, 0, 1, 1, 0, 1, 0,   0, 0), //8 epoch, changed non-quiet slot
    * */



    // todo consensus nonce shall be the same in case if changed quiet slots only

    val genesisBlockId = bytesToId(Utils.doubleSHA256Hash("genesis".getBytes(StandardCharsets.UTF_8)))
    val genesisBlockTimestamp = 1000000
    val networkParams = new TestNetParams(
      sidechainGenesisBlockId = genesisBlockId,
      sidechainGenesisBlockTimestamp = genesisBlockTimestamp,
    ) {override val sidechainGenesisBlockParentId: ModifierId = bytesToId(Utils.doubleSHA256Hash("genesisParent".getBytes(StandardCharsets.UTF_8)))}

    ConsensusParamsUtil.setConsensusParamsForkActivation(Seq(
      ConsensusParamsForkInfo(0, new ConsensusParamsFork(slotsInEpoch, secondsInSlot)),
      ConsensusParamsForkInfo(startFork2, new ConsensusParamsFork(slotsInEpoch2, secondsInSlot2)),
      //ConsensusParamsForkInfo(8, new ConsensusParamsFork(slotsInEpoch3, secondsInSlot3))
    ))
    ConsensusParamsUtil.setConsensusParamsForkTimestampActivation(Seq(
      TimeToEpochUtils.virtualGenesisBlockTimeStamp(networkParams.sidechainGenesisBlockTimestamp),
      TimeToEpochUtils.getTimeStampForEpochAndSlot(networkParams.sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(startFork2), intToConsensusSlotNumber(slotsInEpoch2)),
      //TimeToEpochUtils.getTimeStampForEpochAndSlot(networkParams.sidechainGenesisBlockTimestamp, intToConsensusEpochNumber(8), intToConsensusSlotNumber(slotsInEpoch3))
    ))
    val firstDataProvider = new TestedConsensusDataProvider(slotsPresentationForFirstDataProvider, networkParams)
    val blockIdAndInfosPerEpochForFirstDataProvider = firstDataProvider.blockIdAndInfosPerEpoch
    val epochIdsForFirstDataProvider = firstDataProvider.epochIds
    //Finished preparation
    //ConsensusParamsUtil.getConsensusParamsForkTimestampActivation()

    val consensusInfoForGenesisEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(1)

    val blockIdAndInfoFromStartSecondEpoch = blockIdAndInfosPerEpochForFirstDataProvider(1).head
    val consensusInfoForStartSecondEpoch = firstDataProvider.getFullConsensusEpochInfoForBlock(blockIdAndInfoFromStartSecondEpoch._2.timestamp, blockIdAndInfoFromStartSecondEpoch._2.parentId)

    val blockIdAndInfoFromEndSecondEpoch = blockIdAndInfosPerEpochForFirstDataProvider(1).last
    val consensusInfoForEndSecondEpoch = firstDataProvider.getFullConsensusEpochInfoForBlock(blockIdAndInfoFromEndSecondEpoch._2.timestamp, blockIdAndInfoFromEndSecondEpoch._2.parentId)


    val consensusInfoForEndThirdEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(3)

    val consensusInfoForEndFourthEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(4)

    //Full consensus info is the same for any block in epoch
    assertEquals(consensusInfoForStartSecondEpoch, consensusInfoForEndSecondEpoch)

    //Stake and root hash is the same for second and genesis epoch
    assertEquals(consensusInfoForEndSecondEpoch.stakeConsensusEpochInfo, consensusInfoForGenesisEpoch.stakeConsensusEpochInfo)
    //and stake is as expected
    assertEquals(1000, consensusInfoForEndSecondEpoch.stakeConsensusEpochInfo.totalStake)
    //nd stake root hash as expected
    assertTrue(epochIdsForFirstDataProvider.head.getBytes(StandardCharsets.UTF_8).take(merkleTreeHashLen).sameElements(consensusInfoForGenesisEpoch.stakeConsensusEpochInfo.rootHash))
    //but nonce is the same as well // Is it acceptable? //
    assertEquals(consensusInfoForEndSecondEpoch.nonceConsensusEpochInfo, consensusInfoForGenesisEpoch.nonceConsensusEpochInfo)

    //Stake and root hash is the same for second and third epoch
    assertEquals(consensusInfoForStartSecondEpoch.stakeConsensusEpochInfo, consensusInfoForEndThirdEpoch.stakeConsensusEpochInfo)
    //but nonce is differ todo why?
    assertNotEquals(consensusInfoForStartSecondEpoch.nonceConsensusEpochInfo, consensusInfoForEndThirdEpoch.nonceConsensusEpochInfo)

    //Stake and root hash is differ starting from fourth epoch
    //todo david - why is it different starting from the fourth epoch?
    assertNotEquals(consensusInfoForGenesisEpoch.stakeConsensusEpochInfo, consensusInfoForEndFourthEpoch.stakeConsensusEpochInfo)
    //and nonce is also differ
    assertNotEquals(consensusInfoForGenesisEpoch.nonceConsensusEpochInfo, consensusInfoForEndFourthEpoch.nonceConsensusEpochInfo)

    // regression test
    val nonceConsensusInfoForTenEpoch: NonceConsensusEpochInfo = firstDataProvider.getInfoForCheckingBlockInEpochNumber(10).nonceConsensusEpochInfo
    //Set to true and run if you want to update regression data.
    if (true) {
      val out = new BufferedWriter(new FileWriter("src/test/resources/nonce_calculation_hex"))
      out.write(BytesUtils.toHexString(nonceConsensusInfoForTenEpoch.bytes))
      out.close()
    }

    var bytes: Array[Byte] = Array[Byte]()
    try {
      val classLoader = getClass.getClassLoader
      val file = new FileReader(classLoader.getResource("nonce_calculation_hex").getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine)
    } catch {
      case e: Exception =>
        fail(e.toString)
        return
    }

//    assertEquals(bytes.deep, nonceConsensusInfoForTenEpoch.bytes.deep)

    // Determinism and calculation tests
    val slotsPresentationForSecondDataProvider: List[List[Int]] = List(
      slotsPresentationForFirstDataProvider.head, //2 epoch
      slotsPresentationForFirstDataProvider(1), //3 epoch
      slotsPresentationForFirstDataProvider(2), //4 epoch
      slotsPresentationForFirstDataProvider(3), //5 epoch

      List(0, 1, 1, 0, 0, 0, 0, 1, 1, 0,0,0), //6 epoch, changed quiet slots compared to original
      List(0, 0, 1, 0, 1, 0, 1, 0, 1, 1,0,0), //7 epoch, changed quiet slots
      List(0, 0, 0, 0, 0, 1, 1, 0, 1, 0,   0, 0), //8 epoch, changed non-quiet slot

      slotsPresentationForFirstDataProvider(7), //9 epoch
      slotsPresentationForFirstDataProvider(8) //10 epoch
    )

    val secondDataProvider = new TestedConsensusDataProvider(slotsPresentationForSecondDataProvider, networkParams)
    val blockIdAndInfosPerEpochForSecondDataProvider = secondDataProvider.blockIdAndInfosPerEpoch
    val epochIdsForSecondDataProvider = secondDataProvider.epochIds

    //consensus info shall be calculated the same if all previous infos the same
    val consensusInfoForEndFifthEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(5)
    val consensusInfoForEndFifthEpoch2 = secondDataProvider.getInfoForCheckingBlockInEpochNumber(5)
    assertEquals(consensusInfoForEndFifthEpoch, consensusInfoForEndFifthEpoch2)

    //consensus nonce shall be the same in case if changed quiet slots only
    val consensusInfoForEndSeventhEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(7)
    val consensusInfoForEndSeventhEpoch2 = secondDataProvider.getInfoForCheckingBlockInEpochNumber(7)
    assertEquals(consensusInfoForEndSeventhEpoch.nonceConsensusEpochInfo, consensusInfoForEndSeventhEpoch2.nonceConsensusEpochInfo)
    //Stack root shall not be changed as well due it calculated for epoch 5
    assertEquals(consensusInfoForEndSeventhEpoch.stakeConsensusEpochInfo.rootHash.deep, consensusInfoForEndSeventhEpoch2.stakeConsensusEpochInfo.rootHash.deep)


    //consensus nonce shall be the same in case if changed quiet slots only
    val consensusInfoForEndEightEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(8)
    val consensusInfoForEndEightEpoch2 = secondDataProvider.getInfoForCheckingBlockInEpochNumber(8)

    assertEquals(consensusInfoForEndEightEpoch.nonceConsensusEpochInfo, consensusInfoForEndEightEpoch2.nonceConsensusEpochInfo)
    //but stack root shall be changed (root hash calculated based on last block id in epoch, last block for epoch 6 in tested1 and in tested2 is differ)
    assertNotEquals(consensusInfoForEndEightEpoch.stakeConsensusEpochInfo.rootHash.deep, consensusInfoForEndEightEpoch2.stakeConsensusEpochInfo.rootHash.deep)


    //consensus nonce shall be the changed due non-quiet slots are changed
    val consensusInfoForEndNineEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(9)
    val consensusInfoForEndNineEpoch2 = secondDataProvider.getInfoForCheckingBlockInEpochNumber(9)

    assertNotEquals(consensusInfoForEndNineEpoch.nonceConsensusEpochInfo, consensusInfoForEndNineEpoch2.nonceConsensusEpochInfo)


    //consensus nonce shall be the changed due non-quiet slots are changed in some previous epoch
    val consensusInfoForEndTenEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(10)
    val consensusInfoForEndTenEpoch2 = secondDataProvider.getInfoForCheckingBlockInEpochNumber(10)

    assertNotEquals(consensusInfoForEndTenEpoch.nonceConsensusEpochInfo, consensusInfoForEndTenEpoch2.nonceConsensusEpochInfo)
  }

}
