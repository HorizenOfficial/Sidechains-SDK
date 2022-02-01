package com.horizen.consensus

import java.io.{BufferedReader, BufferedWriter, FileReader, FileWriter}

import com.horizen.chain.SidechainBlockInfo
import com.horizen.fixtures.{CompanionsFixture, SidechainBlockFixture}
import com.horizen.params.{NetworkParams, NetworkParamsUtils, TestNetParams}
import com.horizen.proof.VrfProof
import com.horizen.storage.{InMemoryStorageAdapter, SidechainBlockInfoProvider}
import com.horizen.utils
import com.horizen.utils.{BytesUtils, TimeToEpochUtils, Utils}
import com.horizen.vrf.VrfOutput
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
  with NetworkParamsUtils
  with ScorexLogging {

  require(slotsPresentation.forall(_.size == params.consensusSlotsInEpoch))
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
    consensusDataStorage.addStakeConsensusEpochInfo(epochId, StakeConsensusEpochInfo(epochId.getBytes.take(merkleTreeHashLen), (index + 1) * 1000))}

  //vrfData -- contains data per epoch for filling SidechainBlockInfo, in case if VrfData contains None it means that slot shall be skipped
  private def generateBlockIdsAndInfos(genesisVrfProof: VrfProof,
                                       genesisVrfOutput: VrfOutput,
                                       vrfData: List[List[Option[(VrfProof, VrfOutput)]]]): Seq[Seq[(ModifierId, SidechainBlockInfo)]] = {

    val parentOfGenesisBlock = bytesToId(Utils.doubleSHA256Hash("genesisParent".getBytes))

    val genesisBlockInfo = new SidechainBlockInfo(0, 0, parentOfGenesisBlock, params.sidechainGenesisBlockTimestamp, ModifierSemanticValidity.Valid, Seq(), Seq(), dummyWithdrawalEpochInfo, None, params.sidechainGenesisBlockId)

    val genesisSidechainBlockIdAndInfo = (params.sidechainGenesisBlockId, genesisBlockInfo)
    val accumulator =
      ListBuffer[Seq[(ModifierId, SidechainBlockInfo)]](ListBuffer(genesisSidechainBlockIdAndInfo))

    vrfData.zipWithIndex.foldLeft(accumulator) { case (acc, (processed, index)) =>
      val previousId: ModifierId = acc.last.last._1
      val nextTimeStamp = TimeToEpochUtils.getTimeStampForEpochAndSlot(params, intToConsensusEpochNumber(index + 2), intToConsensusSlotNumber(1))
      val newData =
        generateBlockIdsAndInfosIter(previousId, params.consensusSecondsInSlot, nextTimeStamp, previousId, ListBuffer[(ModifierId, SidechainBlockInfo)](), processed)
      acc.append(newData)
      acc
    }
  }

  @tailrec
  final def generateBlockIdsAndInfosIter(previousId: ModifierId,
                                         secondsInSlot: Int,
                                         nextTimestamp: Long,
                                         lastBlockInPreviousConsensusEpoch: ModifierId,
                                         acc: ListBuffer[(ModifierId, SidechainBlockInfo)],
                                         vrfData: List[Option[(VrfProof, VrfOutput)]]): Seq[(ModifierId, SidechainBlockInfo)] = {
    vrfData.headOption match {
      case Some(Some((vrfProof, vrfOutput))) => {
        val idInfo = generateSidechainBlockInfo(previousId, nextTimestamp, vrfProof, vrfOutput, lastBlockInPreviousConsensusEpoch)
        acc += idInfo
        generateBlockIdsAndInfosIter(idInfo._1, secondsInSlot, nextTimestamp + secondsInSlot, lastBlockInPreviousConsensusEpoch, acc, vrfData.tail)
      }
      case Some(None) => generateBlockIdsAndInfosIter(previousId, secondsInSlot, nextTimestamp + secondsInSlot, lastBlockInPreviousConsensusEpoch, acc, vrfData.tail)
      case None => acc
    }
  }

  private def generateSidechainBlockInfo(parentId: ModifierId, timestamp: Long, vrfProof: VrfProof, vrfOutput: VrfOutput, lastBlockInPreviousConsensusEpoch: ModifierId): (ModifierId, SidechainBlockInfo) = {
    val newBlockId = bytesToId(Utils.doubleSHA256Hash(parentId.getBytes))
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

class ConsensusDataProviderTest extends CompanionsFixture{
  val generator: SidechainBlockFixture = new SidechainBlockFixture {}
  val dummyWithdrawalEpochInfo = utils.WithdrawalEpochInfo(0, 0)


  @Test
  def test(): Unit = {
    val slotsInEpoch = 10
    val slotsPresentationForFirstDataProvider: List[List[Int]] = List(
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
    require(slotsPresentationForFirstDataProvider.forall(_.size == slotsInEpoch))

    val genesisBlockId = bytesToId(Utils.doubleSHA256Hash("genesis".getBytes()))
    val genesisBlockTimestamp = 1000000
    val networkParams = new TestNetParams(
      sidechainGenesisBlockId = genesisBlockId,
      sidechainGenesisBlockTimestamp = genesisBlockTimestamp,
      consensusSlotsInEpoch = slotsInEpoch,
      consensusSecondsInSlot = 100
    ) {override val sidechainGenesisBlockParentId: ModifierId = bytesToId(Utils.doubleSHA256Hash("genesisParent".getBytes))}

    val firstDataProvider = new TestedConsensusDataProvider(slotsPresentationForFirstDataProvider, networkParams)
    val blockIdAndInfosPerEpochForFirstDataProvider = firstDataProvider.blockIdAndInfosPerEpoch
    val epochIdsForFirstDataProvider = firstDataProvider.epochIds
    //Finished preparation

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
    assertTrue(epochIdsForFirstDataProvider.head.getBytes.take(merkleTreeHashLen).sameElements(consensusInfoForGenesisEpoch.stakeConsensusEpochInfo.rootHash))
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

    // regression test
    val nonceConsensusInfoForTenEpoch: NonceConsensusEpochInfo = firstDataProvider.getInfoForCheckingBlockInEpochNumber(10).nonceConsensusEpochInfo
    //Set to true and run if you want to update regression data.
    if (false) {
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

    assertEquals(bytes.deep, nonceConsensusInfoForTenEpoch.bytes.deep)

    // Determinism and calculation tests
    val slotsPresentationForSecondDataProvider: List[List[Int]] = List(
      slotsPresentationForFirstDataProvider.head, //2 epoch
      slotsPresentationForFirstDataProvider(1), //3 epoch
      slotsPresentationForFirstDataProvider(2), //4 epoch
      slotsPresentationForFirstDataProvider(3), //5 epoch
      List(0, 1, 1, 0, 0, 0, 0, 1, 1, 0), //6 epoch, changed quiet slots compared to original
      List(0, 0, 1, 0, 1, 0, 1, 0, 1, 1), //7 epoch, changed quiet slots
      List(1, 0, 1, 0, 1, 1, 1, 0, 1, 0), //8 epoch, changed non-quiet slot
      slotsPresentationForFirstDataProvider(7), //9 epoch
      slotsPresentationForFirstDataProvider(8) //10 epoch
    )

    val secondDataProider = new TestedConsensusDataProvider(slotsPresentationForSecondDataProvider, networkParams)
    val blockIdAndInfosPerEpochForSecondDataProvider = secondDataProider.blockIdAndInfosPerEpoch
    val epochIdsForSecondDataProvider = secondDataProider.epochIds

    //consensus info shall be calculated the same if all previous infos the same
    val consensusInfoForEndFifthEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(5)
    val consensusInfoForEndFifthEpoch2 = secondDataProider.getInfoForCheckingBlockInEpochNumber(5)
    assertEquals(consensusInfoForEndFifthEpoch, consensusInfoForEndFifthEpoch2)

    //consensus nonce shall be the same in case if changed quiet slots only
    val consensusInfoForEndSeventhEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(7)
    val consensusInfoForEndSeventhEpoch2 = secondDataProider.getInfoForCheckingBlockInEpochNumber(7)
    assertEquals(consensusInfoForEndSeventhEpoch.nonceConsensusEpochInfo, consensusInfoForEndSeventhEpoch2.nonceConsensusEpochInfo)
    //Stack root shall not be changed as well due it calculated for epoch 5
    assertEquals(consensusInfoForEndSeventhEpoch.stakeConsensusEpochInfo.rootHash.deep, consensusInfoForEndSeventhEpoch2.stakeConsensusEpochInfo.rootHash.deep)


    //consensus nonce shall be the same in case if changed quiet slots only
    val consensusInfoForEndEightEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(8)
    val consensusInfoForEndEightEpoch2 = secondDataProider.getInfoForCheckingBlockInEpochNumber(8)

    assertEquals(consensusInfoForEndEightEpoch.nonceConsensusEpochInfo, consensusInfoForEndEightEpoch2.nonceConsensusEpochInfo)
    //but stack root shall be changed (root hash calculated based on last block id in epoch, last block for epoch 6 in tested1 and in tested2 is differ)
    assertNotEquals(consensusInfoForEndEightEpoch.stakeConsensusEpochInfo.rootHash.deep, consensusInfoForEndEightEpoch2.stakeConsensusEpochInfo.rootHash.deep)


    //consensus nonce shall be the changed due non-quet slots are changed
    val consensusInfoForEndNineEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(9)
    val consensusInfoForEndNineEpoch2 = secondDataProider.getInfoForCheckingBlockInEpochNumber(9)

    assertNotEquals(consensusInfoForEndNineEpoch.nonceConsensusEpochInfo, consensusInfoForEndNineEpoch2.nonceConsensusEpochInfo)


    //consensus nonce shall be the changed due non-quet slots are changed in some previous epoch
    val consensusInfoForEndTenEpoch = firstDataProvider.getInfoForCheckingBlockInEpochNumber(10)
    val consensusInfoForEndTenEpoch2 = secondDataProider.getInfoForCheckingBlockInEpochNumber(10)

    assertNotEquals(consensusInfoForEndTenEpoch.nonceConsensusEpochInfo, consensusInfoForEndTenEpoch2.nonceConsensusEpochInfo)
  }

}