package io.horizen.fixtures.sidechainblock.generation

import io.horizen.consensus.{ConsensusEpochNumber, VrfMessage}
import io.horizen.proof.VrfProof
import io.horizen.utils.MerklePath
import io.horizen.vrf.VrfOutput


case class PossibleForger(forgingData: SidechainForgingData,
                          merklePathInPreviousEpochOpt: Option[MerklePath],
                          merklePathInPrePreviousEpochOpt: Option[MerklePath],
                          spentInEpochsAgoOpt: Option[Int]) {
  require(!spentInEpochsAgoOpt.exists(_ > PossibleForger.boxToForgerBoxDelayInEpochs))

  def couldBePossibleForgerInNextEpoch: Boolean = spentInEpochsAgoOpt.filter(_ >= PossibleForger.boxToForgerBoxDelayInEpochs).isEmpty

  def createPossibleForgerForTheNextEpoch(newMerklePathOpt: Option[MerklePath]): PossibleForger = {
    copy(
      merklePathInPreviousEpochOpt = newMerklePathOpt,
      merklePathInPrePreviousEpochOpt = merklePathInPreviousEpochOpt,
      spentInEpochsAgoOpt = spentInEpochsAgoOpt.map(_ + 1))
  }

  def isNotSpent: Boolean = spentInEpochsAgoOpt.isEmpty

  def canBeForger(vrfMessage: VrfMessage, totalStake: Long, additionalCheck: Boolean => Boolean, nextEpochNumber: ConsensusEpochNumber): Option[(VrfProof, VrfOutput)] = {
    merklePathInPrePreviousEpochOpt.flatMap(_ => forgingData.canBeForger(vrfMessage, totalStake, additionalCheck, nextEpochNumber))
  }

  override def toString: String = {
    "PossibleForger: " + forgingData.toString
  }
}

object PossibleForger{
  val boxToForgerBoxDelayInEpochs: Int = 2 //0 -- current epoch, 1 -- previous epoch, 2 -- preprevious epoch
}
