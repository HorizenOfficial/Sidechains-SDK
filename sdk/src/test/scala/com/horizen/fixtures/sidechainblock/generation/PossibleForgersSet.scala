package com.horizen.fixtures.sidechainblock.generation
import java.math.BigInteger
import java.util.{Random, ArrayList => JArrayList}

import com.horizen.consensus.{ConsensusSlotNumber, NonceConsensusEpochInfo, StakeConsensusEpochInfo, _}
import com.horizen.proof.VrfProof
import com.horizen.utils._
import com.horizen.vrf.VrfOutput

import scala.collection.immutable.TreeMap


class PossibleForgersSet(forgers: Set[PossibleForger]) {
  private val ordering: Ordering[SidechainForgingData] = Ordering[(Long, BigInteger)].on(x => (x.forgingStakeInfo.stakeAmount, new BigInteger(x.forgerId)))

  val forgingDataToPossibleForger: TreeMap[SidechainForgingData, PossibleForger] = TreeMap(forgers.map(pf => (pf.forgingData.copy(), pf.copy())).toArray:_*)(ordering.reverse)
  require(forgingDataToPossibleForger.size == forgers.size)

  def getRandomPossibleForger(rnd: Random): PossibleForger = forgers.toSeq(rnd.nextInt(forgers.size))

  def getAvailableSidechainForgingData: Set[SidechainForgingData] = forgingDataToPossibleForger.keys.to

  def getNotSpentSidechainForgingData: Set[SidechainForgingData] = forgingDataToPossibleForger.filter{case (forgingData, possibleForger) => possibleForger.isNotSpent}.keys.to

  def getEligibleForger(slotNumber: ConsensusSlotNumber, nonceConsensusEpochInfo: NonceConsensusEpochInfo, totalStake: Long, additionalCheck: Boolean => Boolean): Option[(PossibleForger, VrfProof, VrfOutput)] = {
    val vrfMessage = buildVrfMessage(slotNumber, nonceConsensusEpochInfo)
    forgingDataToPossibleForger
      .values
      .view
      .flatMap{forger => forger.canBeForger(vrfMessage, totalStake, additionalCheck).map{case (proof, vrfOutput) => (forger, proof, vrfOutput)}} //get eligible forgers
      .headOption
  }

  def finishCurrentEpoch(): (PossibleForgersSet, StakeConsensusEpochInfo) = {
    val possibleForgersForNextEpoch: Seq[PossibleForger] = getPossibleForgersForNextEpoch

    val totalStake = possibleForgersForNextEpoch.withFilter(_.isNotSpent).map(_.forgingData.forgingStakeInfo.stakeAmount).sum
    val merkleTreeForEndOfEpoch: MerkleTree = buildMerkleTree(possibleForgersForNextEpoch.map(_.forgingData.forgingStakeInfo.hash))
    val merkleTreeForEndOfEpochRootHash = merkleTreeForEndOfEpoch.rootHash()

    val forgingDataWithUpdatedMerkleTreePathAndMaturity: Seq[PossibleForger] =
      possibleForgersForNextEpoch
        .zipWithIndex.map{case (possibleForger, index) =>
        possibleForger.createPossibleForgerForTheNextEpoch(Some(merkleTreeForEndOfEpoch.getMerklePathForLeaf(index)))}

    val stakeEpochInfo: StakeConsensusEpochInfo = StakeConsensusEpochInfo(merkleTreeForEndOfEpochRootHash, totalStake)
    val newForgers = new PossibleForgersSet(Set(forgingDataWithUpdatedMerkleTreePathAndMaturity.toArray:_*))

    (newForgers, stakeEpochInfo)
  }

  private def getPossibleForgersForNextEpoch: Seq[PossibleForger] = {
    forgingDataToPossibleForger.values.filter(_.couldBePossibleForgerInNextEpoch).to
  }

  def createModified(generationRules: GenerationRules): PossibleForgersSet = {

    val forgingBoxToSpent = generationRules.forgingBoxesToSpent
    val forgingBoxToAdd = generationRules.forgingBoxesToAdd

    val withoutSpentKeys = forgingDataToPossibleForger.keys.toSet -- forgingBoxToSpent
    require((withoutSpentKeys.size + forgingBoxToSpent.size) == forgingDataToPossibleForger.size)

    val withoutSpentPossibleForgers: Set[PossibleForger] = withoutSpentKeys.collect(forgingDataToPossibleForger)
    val spentPossibleForgers: Set[PossibleForger] = forgingBoxToSpent.collect(forgingDataToPossibleForger).map(possibleForger => possibleForger.copy(spentInEpochsAgoOpt = Some(0)))
    val newPossibleForgers: Set[PossibleForger] = forgingBoxToAdd.map(PossibleForger(_, merklePathInPreviousEpochOpt = None, merklePathInPrePreviousEpochOpt = None, spentInEpochsAgoOpt = None))

    new PossibleForgersSet(withoutSpentPossibleForgers ++ spentPossibleForgers ++ newPossibleForgers)
  }

  private def buildMerkleTree(ids: Iterable[Array[Byte]]): MerkleTree = {
    val leavesForMerkleTree =
      ids.foldLeft(new JArrayList[Array[Byte]]()) {(acc, id) =>
        acc.add(id)
        acc
      }

    val filler = leavesForMerkleTree.get(leavesForMerkleTree.size() - 1)
    val additionalLeaves = SidechainBlocksGenerator.merkleTreeSize - leavesForMerkleTree.size()
    (1 to additionalLeaves).foreach(_ => leavesForMerkleTree.add(filler))

    val resultTree = MerkleTree.createMerkleTree(leavesForMerkleTree)
    resultTree
  }
}