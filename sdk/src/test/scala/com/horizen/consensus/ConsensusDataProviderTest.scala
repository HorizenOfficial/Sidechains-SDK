package com.horizen.consensus

import com.horizen.chain.SidechainBlockInfo
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.params.{NetworkParams, NetworkParamsUtils, TestNetParams}
import com.horizen.proof.VrfProof
import com.horizen.storage.{InMemoryStorageAdapter, SidechainBlockInfoProvider}
import com.horizen.utils
import com.horizen.utils.Utils
import com.horizen.vrf.VrfProofHash
import org.junit.Assert._
import org.junit.Test
import scorex.core.consensus.ModifierSemanticValidity
import scorex.util._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer



class TestedConsensusDataProvider(slotsPresentation: List[List[Int]],
                                  val params: NetworkParams)
  extends ConsensusDataProvider
  with TimeToEpochSlotConverter
  with NetworkParamsUtils
  with ScorexLogging {

  require(slotsPresentation.forall(_.size == params.consensusSlotsInEpoch))
  private val dummyWithdrawalEpochInfo = utils.WithdrawalEpochInfo(0, 0)

  private val genesisVrfProof = new VrfProof("genesis".getBytes())
  private val genesisVrfProofHash = new VrfProofHash("genesisHash".getBytes())

  private val vrfData = slotsPresentationToVrfData(slotsPresentation)
  val blockIdAndInfosPerEpoch: Seq[Seq[(ModifierId, SidechainBlockInfo)]] =
    generateBlockIdsAndInfos(genesisVrfProof, genesisVrfProofHash, vrfData)
  val epochIds: Seq[ConsensusEpochId] = blockIdAndInfosPerEpoch.map(epoch => blockIdToEpochId(epoch.last._1))


  val storage = new BlocksInfoProvider()
  blockIdAndInfosPerEpoch.flatten.foreach{case (id, info) => storage.addBlockInfo(id, info)}

  val consensusDataStorage = new ConsensusDataStorage(new InMemoryStorageAdapter())
  epochIds.zipWithIndex.foreach{case (epochId, index) =>
    consensusDataStorage.addStakeConsensusEpochInfo(epochId, StakeConsensusEpochInfo(epochId.getBytes.take(merkleTreeHashLen), (index + 1) * 1000))}

  //vrfData -- contains data per epoch for filling SidechainBlockInfo, in case if VrfData contains None it means that slot shall be skipped
  private def generateBlockIdsAndInfos(genesisVrfProof: VrfProof,
                                       genesisVrfProofHash: VrfProofHash,
                                       vrfData: List[List[Option[(VrfProof, VrfProofHash)]]]): Seq[Seq[(ModifierId, SidechainBlockInfo)]] = {


    val genesisBlockInfo = new SidechainBlockInfo(
      0,
      0,
      bytesToId(Utils.doubleSHA256Hash("genesisParent".getBytes)),
      params.sidechainGenesisBlockTimestamp,
      ModifierSemanticValidity.Valid,
      Seq(),
      dummyWithdrawalEpochInfo,
      genesisVrfProof,
      genesisVrfProofHash)

    val genesisSidechainBlockIdAndInfo = (params.sidechainGenesisBlockId, genesisBlockInfo)
    val accumulator =
      ListBuffer[Seq[(ModifierId, SidechainBlockInfo)]](ListBuffer(genesisSidechainBlockIdAndInfo))

    vrfData.zipWithIndex.foldLeft(accumulator) { case (acc, (processed, index)) =>
      val previousId = acc.last.last._1
      val nextTimeStamp = getTimeStampForEpochAndSlot(intToConsensusEpochNumber(index + 2), intToConsensusSlotNumber(1))
      val newData =
        generateBlockIdsAndInfosIter(previousId, params.consensusSecondsInSlot, nextTimeStamp, ListBuffer[(ModifierId, SidechainBlockInfo)](), processed)
      acc.append(newData)
      acc
    }
  }

  @tailrec
  final def generateBlockIdsAndInfosIter(previousId: ModifierId,
                                         secondsInSlot: Int,
                                         nextTimestamp: Long,
                                         acc: ListBuffer[(ModifierId, SidechainBlockInfo)],
                                         vrfData: List[Option[(VrfProof, VrfProofHash)]]): Seq[(ModifierId, SidechainBlockInfo)] = {
    vrfData.headOption match {
      case Some(Some((vrfProof, vrfProofHash))) => {
        val idInfo = generateSidechainBlockInfo(previousId, nextTimestamp, vrfProof, vrfProofHash)
        acc += idInfo
        generateBlockIdsAndInfosIter(idInfo._1, secondsInSlot, nextTimestamp + secondsInSlot, acc, vrfData.tail)
      }
      case Some(None) => generateBlockIdsAndInfosIter(previousId, secondsInSlot, nextTimestamp + secondsInSlot, acc, vrfData.tail)
      case None => acc
    }
  }

  private def generateSidechainBlockInfo(parentId: ModifierId, timestamp: Long, vrfProof: VrfProof, vrfProofHash: VrfProofHash): (ModifierId, SidechainBlockInfo) = {
    val newBlockId = bytesToId(Utils.doubleSHA256Hash(parentId.getBytes))
    val blockInfo =
      new SidechainBlockInfo(0, 0, parentId, timestamp, ModifierSemanticValidity.Valid, Seq(), dummyWithdrawalEpochInfo, vrfProof, vrfProofHash)

    (newBlockId, blockInfo)
  }


  private def slotsPresentationToVrfData(slotsRepresentations: List[List[Int]], prefix: String = ""):  List[List[Option[(VrfProof, VrfProofHash)]]] = {
    slotsRepresentations.zipWithIndex.map{case (slotsRepresentationsForEpoch, epochIndex) =>
      slotsRepresentationsForEpoch.zipWithIndex.map{
        case (1, slotIndex) =>
          Some(new VrfProof(s"${prefix}proof${epochIndex}${slotIndex}".getBytes), new VrfProofHash(s"${prefix}proofHash${epochIndex}${slotIndex}".getBytes))
        case (0, _) =>
          None
        case _ => throw new IllegalArgumentException
      }
    }
  }

  def getFullConsensusEpochInfoForBlock(idInfo: (ModifierId, SidechainBlockInfo)): FullConsensusEpochInfo = {
    getFullConsensusEpochInfoForBlock(idInfo._1, idInfo._2)
  }

  def getInfoForCheckingBlockInEpochNumber(epochNumber: Int): FullConsensusEpochInfo = {
    getFullConsensusEpochInfoForBlock(blockIdAndInfosPerEpoch(epochNumber - 1).head)
  }
}

class BlocksInfoProvider extends SidechainBlockInfoProvider {
  private val storage = mutable.Map[ModifierId, SidechainBlockInfo]()

  override def blockInfoById(blockId: ModifierId): SidechainBlockInfo = storage(blockId)
  def addBlockInfo(blockId: ModifierId, sidechainBlockInfo: SidechainBlockInfo): Unit = storage.put(blockId, sidechainBlockInfo)
}

class ConsensusDataProviderTest extends CompanionsFixture{
  val generator: SidechainBlockFixture = new SidechainBlockFixture {}
  val dummyWithdrawalEpochInfo = utils.WithdrawalEpochInfo(0, 0)


  @Test
  def test(): Unit = {
    val slotsInEpoch = 10
    val slotsPresentation: List[List[Int]] = List(
//   1 -- block in slot is present; 0 -- no block for slot
//   slots 1  2  3  4  5  6  7  8  9  10
      List(1, 1, 1, 1, 1, 1, 1, 1, 1, 1), //2 epoch
      List(1, 1, 0, 0, 1, 1, 1, 1, 0, 0), //3 epoch
      List(1, 1, 0, 0, 1, 1, 1, 1, 0, 0), //4 epoch
      List(0, 0, 0, 0, 1, 1, 1, 1, 0, 0), //5 epoch
      List(1, 0, 0, 0, 0, 0, 0, 0, 0, 1), //6 epoch
      List(1, 0, 1, 0, 1, 0, 1, 0, 1, 0), //7 epoch
      List(1, 0, 1, 0, 1, 0, 1, 0, 1, 0), //8 epoch
      List(0, 1, 1, 0, 1, 0, 1, 1, 1, 1), //9 epoch
      List(0, 1, 1, 0, 1, 0, 1, 1, 0, 1), //10 epoch

    )
    require(slotsPresentation.forall(_.size == slotsInEpoch))

    val genesisBlockId = bytesToId(Utils.doubleSHA256Hash("genesis".getBytes()))
    val genesisBlockTimestamp = 1000000
    val networkParams = TestNetParams(
      sidechainGenesisBlockId = genesisBlockId,
      sidechainGenesisBlockTimestamp = genesisBlockTimestamp,
      consensusSlotsInEpoch = slotsInEpoch,
      consensusSecondsInSlot = 100
    )

    val tested1 = new TestedConsensusDataProvider(slotsPresentation, networkParams)
    val blockIdAndInfosPerEpoch1 = tested1.blockIdAndInfosPerEpoch
    val epochIds1 = tested1.epochIds
    //Finished preparation

    val consensusInfoForGenesisEpoch = tested1.getInfoForCheckingBlockInEpochNumber(1)

    val blockIdAndInfoFromStartSecondEpoch = blockIdAndInfosPerEpoch1(1).head
    val consensusInfoForStartSecondEpoch = tested1.getFullConsensusEpochInfoForBlock(blockIdAndInfoFromStartSecondEpoch)

    val blockIdAndInfoFromEndSecondEpoch = blockIdAndInfosPerEpoch1(1).last
    val consensusInfoForEndSecondEpoch = tested1.getFullConsensusEpochInfoForBlock(blockIdAndInfoFromEndSecondEpoch)

    val consensusInfoForEndThirdEpoch = tested1.getInfoForCheckingBlockInEpochNumber(3)

    val consensusInfoForEndFourthEpoch = tested1.getInfoForCheckingBlockInEpochNumber(4)

    //Full consensus info is the same for any block in epoch
    assertEquals(consensusInfoForStartSecondEpoch, consensusInfoForEndSecondEpoch)

    //Stake and root hash is the same for second and genesis epoch
    assertEquals(consensusInfoForEndSecondEpoch.stakeConsensusEpochInfo, consensusInfoForGenesisEpoch.stakeConsensusEpochInfo)
    //and stake is as expected
    assertEquals(1000, consensusInfoForEndSecondEpoch.stakeConsensusEpochInfo.totalStake)
    //nd stake root hash as expected
    assertTrue(epochIds1.head.getBytes.take(merkleTreeHashLen).sameElements(consensusInfoForGenesisEpoch.stakeConsensusEpochInfo.rootHash))
    //but nonce is the same as well // Is it acceptable? //
    assertEquals(consensusInfoForEndSecondEpoch.nonceConsensusEpochInfo, consensusInfoForGenesisEpoch.nonceConsensusEpochInfo)

    //Stake and root hash is the same for second and third epoch
    assertEquals(consensusInfoForStartSecondEpoch.stakeConsensusEpochInfo, consensusInfoForEndThirdEpoch.stakeConsensusEpochInfo)
    //but nonce is differ
    assertNotEquals(consensusInfoForStartSecondEpoch.nonceConsensusEpochInfo, consensusInfoForEndThirdEpoch.nonceConsensusEpochInfo)

    //Stake and root hash is differ starting from fourth epoch
    assertNotEquals(consensusInfoForGenesisEpoch.stakeConsensusEpochInfo, consensusInfoForEndFourthEpoch.stakeConsensusEpochInfo)
    //and nonce is also differ
    assertNotEquals(consensusInfoForGenesisEpoch.nonceConsensusEpochInfo, consensusInfoForEndFourthEpoch.nonceConsensusEpochInfo)

    //ConsensusInfo is the same: we got information for adding block to 5th epoch in both cases
    val infoForFive = tested1.getInfoForCheckingBlockInEpochNumber(5)
    val infoByLastBlockInFourEpochForFive = tested1.getFullConsensusEpochInfoForNextBlock(blockIdAndInfosPerEpoch1(3).last._1, intToConsensusEpochNumber(5))
    assertEquals(infoForFive, infoByLastBlockInFourEpochForFive)
    //but we shall get other info if we try to get consensus info for different epoch number
    val infoByLastBlockInFourEpochForFour = tested1.getFullConsensusEpochInfoForNextBlock(blockIdAndInfosPerEpoch1(3).last._1, intToConsensusEpochNumber(4))
    assertNotEquals(infoForFive, infoByLastBlockInFourEpochForFour)
    //and nonce consensus data shall be differ if we try to calculate it for first block in epoch
    val infoByFirstBlockInFourEpochForFive = tested1.getFullConsensusEpochInfoForNextBlock(blockIdAndInfosPerEpoch1(3).head._1, intToConsensusEpochNumber(5))
    assertNotEquals(infoByFirstBlockInFourEpochForFive.nonceConsensusEpochInfo, infoForFive.nonceConsensusEpochInfo)
    assertEquals(infoByFirstBlockInFourEpochForFive.stakeConsensusEpochInfo, infoForFive.stakeConsensusEpochInfo)
    assertNotEquals(infoByFirstBlockInFourEpochForFive.nonceConsensusEpochInfo, infoByLastBlockInFourEpochForFive.nonceConsensusEpochInfo)
    assertEquals(infoByFirstBlockInFourEpochForFive.stakeConsensusEpochInfo, infoByLastBlockInFourEpochForFive.stakeConsensusEpochInfo)


    val slotsPresentation2: List[List[Int]] = List(
      slotsPresentation(0), //2 epoch
      slotsPresentation(1), //3 epoch
      slotsPresentation(2), //4 epoch
      slotsPresentation(3), //5 epoch
      List(0, 1, 1, 0, 0, 0, 0, 1, 1, 0), //6 epoch, changed quiet slots compared to original
      List(0, 0, 1, 0, 1, 0, 1, 0, 1, 1), //7 epoch, changed quiet slots
      List(1, 0, 1, 0, 1, 1, 1, 0, 1, 0), //8 epoch, changed non-quiet slot
      slotsPresentation(7), //9 epoch
      slotsPresentation(8) //10 epoch
    )

    val tested2 = new TestedConsensusDataProvider(slotsPresentation2, networkParams)
    val blockIdAndInfosPerEpoch2 = tested2.blockIdAndInfosPerEpoch
    val epochIds2 = tested2.epochIds

    //consensus info shall be calculated the same if all previous infos the same
    val consensusInfoForEndFifthEpoch = tested1.getInfoForCheckingBlockInEpochNumber(5)
    val consensusInfoForEndFifthEpoch2 = tested2.getInfoForCheckingBlockInEpochNumber(5)
    assertEquals(consensusInfoForEndFifthEpoch, consensusInfoForEndFifthEpoch2)

    //consensus nonce shall be the same in case if changed quiet slots only
    val consensusInfoForEndSeventhEpoch = tested1.getInfoForCheckingBlockInEpochNumber(7)
    val consensusInfoForEndSeventhEpoch2 = tested2.getInfoForCheckingBlockInEpochNumber(7)
    assertEquals(consensusInfoForEndSeventhEpoch.nonceConsensusEpochInfo, consensusInfoForEndSeventhEpoch2.nonceConsensusEpochInfo)
    //Stack root shall not be changed as well due it calculated for epoch 5
    assertEquals(consensusInfoForEndSeventhEpoch.stakeConsensusEpochInfo.rootHash.deep, consensusInfoForEndSeventhEpoch2.stakeConsensusEpochInfo.rootHash.deep)


    //consensus nonce shall be the same in case if changed quiet slots only
    val consensusInfoForEndEightEpoch = tested1.getInfoForCheckingBlockInEpochNumber(8)
    val consensusInfoForEndEightEpoch2 = tested2.getInfoForCheckingBlockInEpochNumber(8)

    assertEquals(consensusInfoForEndEightEpoch.nonceConsensusEpochInfo, consensusInfoForEndEightEpoch2.nonceConsensusEpochInfo)
    //but stack root shall be changed (root hash calculated based on last block id in epoch, last block for epoch 6 in tested1 and in tested2 is differ)
    assertNotEquals(consensusInfoForEndEightEpoch.stakeConsensusEpochInfo.rootHash.deep, consensusInfoForEndEightEpoch2.stakeConsensusEpochInfo.rootHash.deep)


    //consensus nonce shall be the changed due non-quet slots are changed
    val consensusInfoForEndNineEpoch = tested1.getInfoForCheckingBlockInEpochNumber(9)
    val consensusInfoForEndNineEpoch2 = tested2.getInfoForCheckingBlockInEpochNumber(9)

    assertNotEquals(consensusInfoForEndNineEpoch.nonceConsensusEpochInfo, consensusInfoForEndNineEpoch2.nonceConsensusEpochInfo)


    //consensus nonce shall be the changed due non-quet slots are changed in some previous epoch
    val consensusInfoForEndTenEpoch = tested1.getInfoForCheckingBlockInEpochNumber(10)
    val consensusInfoForEndTenEpoch2 = tested2.getInfoForCheckingBlockInEpochNumber(10)

    assertNotEquals(consensusInfoForEndTenEpoch.nonceConsensusEpochInfo, consensusInfoForEndTenEpoch2.nonceConsensusEpochInfo)
  }

}