package com.horizen.fixtures.sidechainblock.generation
import java.math.BigInteger
import java.util.{Random, ArrayList => JArrayList}

import com.horizen.consensus.StakeConsensusEpochInfo
import com.horizen.utils._
import com.horizen.vrf.VRFProof

import scala.collection.immutable.TreeMap


class PossibleForgersSet(forgers: Set[PossibleForger]) {
  private val ordering: Ordering[SidechainForgingData] = Ordering.by{forgingData: SidechainForgingData =>
    new BigInteger(forgingData.key.bytes())
  }

  val forgingDataToPossibleForger: TreeMap[SidechainForgingData, PossibleForger] = TreeMap(forgers.map(pf => (pf.forgingData.copy(), pf.copy())).toArray:_*)(ordering)
  require(forgingDataToPossibleForger.size == forgers.size)

  def getRandomPossibleForger(rnd: Random): PossibleForger = forgers.toSeq(rnd.nextInt(forgers.size))

  def getAvailableSidechainForgingData: Set[SidechainForgingData] = forgingDataToPossibleForger.keys.to

  def getNotSpentSidechainForgingData: Set[SidechainForgingData] = forgingDataToPossibleForger.filter{case (forgingData, possibleForger) => possibleForger.isNotSpent}.keys.to

  def getEligibleForger(vrfMessage: Array[Byte], totalStake: Long, additionalCheck: Boolean => Boolean): Option[(PossibleForger, VRFProof)] = {
    forgingDataToPossibleForger
      .values
      .toStream
      .flatMap{forger => forger.canBeForger(vrfMessage, totalStake, additionalCheck).map(proof => (forger, proof))} //get eligible forgers
      .headOption
  }

  def finishCurrentEpoch(): (PossibleForgersSet, StakeConsensusEpochInfo) = {
    val possibleForgersForNextEpoch: Seq[PossibleForger] = getPossibleForgersForNextEpoch

    val totalStake = possibleForgersForNextEpoch.map(_.forgingData.forgerBox.value()).sum
    val merkleTreeForEndOfEpoch: MerkleTree = buildMerkleTree(possibleForgersForNextEpoch.map(_.forgingData.forgerBox.id()))
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