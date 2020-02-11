package com.horizen.storage

import java.util.{ArrayList => JArrayList, List => JList}

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.horizen.SidechainTypes
import com.horizen.utils.{Pair => JPair}

import scala.util._
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.consensus.{ConsensusEpochNumber, ForgingStakeInfo, ForgingStakeInfoSerializer}
import com.horizen.utils.{ByteArrayWrapper, ListSerializer, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging
import com.horizen.consensus._

class SidechainStateStorage(storage: Storage, sidechainBoxesCompanion: SidechainBoxesCompanion)
  extends ScorexLogging
  with SidechainTypes
{
  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  private[horizen] val withdrawalEpochInformationKey = calculateKey("withdrawalEpochInformation".getBytes)
  private val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequestBox](WithdrawalRequestBoxSerializer.getSerializer)

  private[horizen] val consensusEpochKey = calculateKey("consensusEpoch".getBytes)
  private[horizen] val forgingStakesAmountKey = calculateKey("forgingStakesAmount".getBytes)
  private[horizen] val forgingStakesInfoKey = calculateKey("forgingStakes".getBytes)
  private val forgingStakeInfoSerializer = new ListSerializer[ForgingStakeInfo](ForgingStakeInfoSerializer)

  private[horizen] def getWithdrawalRequestsKey(withdrawalEpoch: Int) : ByteArrayWrapper = {
    calculateKey(Bytes.concat("withdrawalRequests".getBytes, Ints.toByteArray(withdrawalEpoch)))
  }

  def calculateKey(boxId : Array[Byte]) : ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
  }

  def getBox(boxId : Array[Byte]) : Option[SidechainTypes#SCB] = {
    storage.get(calculateKey(boxId)) match {
      case v if v.isPresent =>
        sidechainBoxesCompanion.parseBytesTry(v.get().data) match {
          case Success(box) => Option(box)
          case Failure(exception) =>
            log.error("Error while WalletBox parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getWithdrawalEpochInfo: Option[WithdrawalEpochInfo] = {
    storage.get(withdrawalEpochInformationKey).asScala match {
      case Some(baw) =>
        WithdrawalEpochInfoSerializer.parseBytesTry(baw.data) match {
          case Success(withdrawalEpochInfo) => Option(withdrawalEpochInfo)
          case Failure(exception) =>
            log.error("Error while withdrawal epoch info information parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getWithdrawalRequests(epoch: Int): JList[WithdrawalRequestBox] = { // Do we need to return JList here?
    storage.get(getWithdrawalRequestsKey(epoch)).asScala match {
      case Some(baw) =>
        withdrawalRequestSerializer.parseBytesTry(baw.data) match {
          case Success(withdrawalRequests) => withdrawalRequests
          case Failure(exception) =>
            log.error("Error while withdrawal requests parsing.", exception)
            new JArrayList[WithdrawalRequestBox]()
        }
      case _ => new JArrayList[WithdrawalRequestBox]()
    }
  }

  def getConsensusEpochNumber: Option[ConsensusEpochNumber] = {
    storage.get(consensusEpochKey).asScala match {
      case Some(baw) =>
        Try {
          Ints.fromByteArray(baw.data)
        } match {
          case Success(epoch) => Some(intToConsensusEpochNumber(epoch))
          case Failure(exception) =>
            log.error("Error while consensus epoch information parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getForgingStakesAmount: Option[Long] = {
    storage.get(forgingStakesAmountKey).asScala match {
      case Some(baw) =>
        Try {
          Longs.fromByteArray(baw.data)
        } match {
          case Success(epoch) => Some(epoch)
          case Failure(exception) =>
            log.error("Error while forging stakes amount parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getForgingStakesInfo: Option[Seq[ForgingStakeInfo]] = {
    storage.get(forgingStakesInfoKey).asScala match {
      case Some(baw) =>
        forgingStakeInfoSerializer.parseBytesTry(baw.data) match {
          case Success(stakesInfo) => Some(stakesInfo.asScala)
          case Failure(exception) =>
            log.error("Error while forging stakes parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def update(version: ByteArrayWrapper,
             withdrawalEpochInfo: WithdrawalEpochInfo,
             boxUpdateList: Set[SidechainTypes#SCB],
             boxIdsRemoveList: Set[Array[Byte]],
             withdrawalRequestAppendSeq: Seq[WithdrawalRequestBox],
             forgingStakesToAppendSeq: Seq[ForgingStakeInfo],
             consensusEpoch: ConsensusEpochNumber): Try[SidechainStateStorage] = Try {
    require(withdrawalEpochInfo != null, "WithdrawalEpochInfo must be NOT NULL.")
    require(boxUpdateList != null, "List of Boxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveList != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    require(!boxUpdateList.contains(null), "Box to add/update must be NOT NULL.")
    require(!boxIdsRemoveList.contains(null), "BoxId to remove must be NOT NULL.")
    require(withdrawalRequestAppendSeq != null, "Seq of WithdrawalRequests to append must be NOT NULL. Use empty Seq instead.")
    require(!withdrawalRequestAppendSeq.contains(null), "WithdrawalRequest to append must be NOT NULL.")
    require(forgingStakesToAppendSeq != null, "Seq of ForgerStakes to append must be NOT NULL. Use empty Seq instead.")
    require(!forgingStakesToAppendSeq.contains(null), "ForgerStake to append must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    // Update boxes data
    for (r <- boxIdsRemoveList)
      removeList.add(calculateKey(r))

    for (b <- boxUpdateList)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](calculateKey(b.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))))

    // Update Withdrawal epoch related data
    updateList.add(new JPair(withdrawalEpochInformationKey,
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfo))))

    if (withdrawalRequestAppendSeq.nonEmpty) {
      val withdrawalRequestList = getWithdrawalRequests(withdrawalEpochInfo.epoch)

      withdrawalRequestList.addAll(withdrawalRequestAppendSeq.asJava)

      updateList.add(new JPair(getWithdrawalRequestsKey(withdrawalEpochInfo.epoch),
        new ByteArrayWrapper(withdrawalRequestSerializer.toBytes(withdrawalRequestList))))
    }

    // Update Consensus related data
    if(getConsensusEpochNumber.getOrElse(intToConsensusEpochNumber(0)) != consensusEpoch)
      updateList.add(new JPair(consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(consensusEpoch))))

    val (forgingStakesInfoSeq, forgingStakesAmount) = applyForgingStakesChanges(boxIdsRemoveList, forgingStakesToAppendSeq)
    updateList.add(new JPair(
      forgingStakesInfoKey,
      new ByteArrayWrapper(forgingStakeInfoSerializer.toBytes(forgingStakesInfoSeq.asJava))
    ))
    updateList.add(new JPair(
      forgingStakesAmountKey,
      new ByteArrayWrapper(Longs.toByteArray(forgingStakesAmount))
    ))

    storage.update(version, updateList, removeList)

    this
  }

  private def applyForgingStakesChanges(boxIdsToRemove: Set[Array[Byte]], forgingStakesToAppendSeq: Seq[ForgingStakeInfo]): (Seq[ForgingStakeInfo], Long) = {
    getForgingStakesInfo match {
      case Some(currentStakesInfoSeq) =>
        // Separate removedStakes from current stakes
        val boxIdsToRemoveBAW = boxIdsToRemove.map(new ByteArrayWrapper(_))
        val (removedStakes, existentStakes) = currentStakesInfoSeq.partition(stakeInfo => boxIdsToRemoveBAW.contains(new ByteArrayWrapper(stakeInfo.boxId)))

        // Update current stakes amount
        val stakesAmount = getForgingStakesAmount.getOrElse(0L) - removedStakes.map(_.value).sum + forgingStakesToAppendSeq.map(_.value).sum

        (existentStakes ++ forgingStakesToAppendSeq, stakesAmount)
      case None =>
        (forgingStakesToAppendSeq, forgingStakesToAppendSeq.foldLeft(0L)(_ + _.value))
    }
  }

  def lastVersionId : Option[ByteArrayWrapper] = {
    val lastVersion = storage.lastVersionID()
    if (lastVersion.isPresent)
      Some(lastVersion.get())
    else
      None
  }

  def rollbackVersions : Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Try[SidechainStateStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty

}
