package com.horizen.storage


import java.util.{ArrayList => JArrayList}

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.SidechainTypes
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.consensus._
import com.horizen.utils.{ByteArrayWrapper, ListSerializer, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer, Pair => JPair, _}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters._
import scala.util._

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

  private val undefinedWithdrawalEpochCounter: Int = -1
  private[horizen] def getWithdrawalEpochCounterKey(withdrawalEpoch: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("withdrawalEpochCounter".getBytes, Ints.toByteArray(withdrawalEpoch)))
  }

  private[horizen] def getWithdrawalRequestsKey(withdrawalEpoch: Int, counter: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("withdrawalRequests".getBytes, Ints.toByteArray(withdrawalEpoch), Ints.toByteArray(counter)))
  }

  private[horizen] def getWithdrawalBlockKey(epoch: Int): ByteArrayWrapper = {
    calculateKey(("Withdrawal block - " + epoch).getBytes)
  }

  private val lastWithdrawalCertificatePreviousMcBlockHashKey: ByteArrayWrapper = {
    calculateKey("Previous MC block hash Key".getBytes)
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

  def getWithdrawalEpochCounter(epoch: Int): Int = {
    storage.get(getWithdrawalEpochCounterKey(epoch)).asScala match {
      case Some(baw) =>
        Try {
          Ints.fromByteArray(baw.data)
        }.toOption.getOrElse(undefinedWithdrawalEpochCounter)
      case _ => undefinedWithdrawalEpochCounter
    }
  }

  def getWithdrawalRequests(epoch: Int): Seq[WithdrawalRequestBox] = {
    // Aggregate withdrawal requests until reaching the counter, where the key is not present in the storage.
    val withdrawalRequests: ListBuffer[WithdrawalRequestBox] = ListBuffer()
    val lastCounter: Int = getWithdrawalEpochCounter(epoch)
    for(counter <- 0 to lastCounter) {
      storage.get(getWithdrawalRequestsKey(epoch, counter)).asScala match {
        case Some(baw) =>
          withdrawalRequestSerializer.parseBytesTry(baw.data) match {
            case Success(wr) =>
              withdrawalRequests.appendAll(wr.asScala)
            case Failure(exception) =>
              throw new IllegalStateException("Error while withdrawal requests parsing.", exception)
          }
        case None =>
          throw new IllegalStateException("Error while withdrawal requests retrieving: record expected to exist.")
      }
    }
    withdrawalRequests
  }

  def getUnprocessedWithdrawalRequests(epoch: Int) : Option[Seq[WithdrawalRequestBox]] = {
    storage.get(getWithdrawalBlockKey(epoch)) match {
      case v if v.isPresent => None
      case _ => Some(getWithdrawalRequests(epoch))
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

  def getLastCertificateEndEpochMcBlockHashOpt: Option[Array[Byte]] = storage.get(lastWithdrawalCertificatePreviousMcBlockHashKey).asScala.map(_.data)

  def update(version: ByteArrayWrapper,
             withdrawalEpochInfo: WithdrawalEpochInfo,
             boxUpdateList: Set[SidechainTypes#SCB],
             boxIdsRemoveSet: Set[ByteArrayWrapper],
             withdrawalRequestAppendSeq: Seq[WithdrawalRequestBox],
             consensusEpoch: ConsensusEpochNumber,
             withdrawalEpochCertificateOpt: Option[WithdrawalEpochCertificate]): Try[SidechainStateStorage] = Try {
    require(withdrawalEpochInfo != null, "WithdrawalEpochInfo must be NOT NULL.")
    require(boxUpdateList != null, "List of Boxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveSet != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    require(!boxUpdateList.contains(null), "Box to add/update must be NOT NULL.")
    require(!boxIdsRemoveSet.contains(null), "BoxId to remove must be NOT NULL.")
    require(withdrawalRequestAppendSeq != null, "Seq of WithdrawalRequests to append must be NOT NULL. Use empty Seq instead.")
    require(!withdrawalRequestAppendSeq.contains(null), "WithdrawalRequest to append must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    // Update boxes data
    for (r <- boxIdsRemoveSet)
      removeList.add(calculateKey(r.data))

    for (b <- boxUpdateList)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](calculateKey(b.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))))

    // Update Withdrawal epoch related data
    updateList.add(new JPair(withdrawalEpochInformationKey,
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfo))))

    if (withdrawalRequestAppendSeq.nonEmpty) {
      // Calculate the next counter for storing withdrawal requests without duplication previously stored ones.
      val nextWithdrawalEpochCounter: Int = getWithdrawalEpochCounter(withdrawalEpochInfo.epoch) + 1

      updateList.add(new JPair(getWithdrawalEpochCounterKey(withdrawalEpochInfo.epoch),
        new ByteArrayWrapper(Ints.toByteArray(nextWithdrawalEpochCounter))))

      updateList.add(new JPair(getWithdrawalRequestsKey(withdrawalEpochInfo.epoch, nextWithdrawalEpochCounter),
        new ByteArrayWrapper(withdrawalRequestSerializer.toBytes(withdrawalRequestAppendSeq.asJava))))
    }

    // If withdrawal epoch switched to the next one, then remove outdated withdrawal related records and counters (2 epochs before).
    val isWithdrawalEpochSwitched: Boolean = getWithdrawalEpochInfo match {
      case Some(storedEpochInfo) => storedEpochInfo.epoch != withdrawalEpochInfo.epoch
      case _ => false
    }
    if (isWithdrawalEpochSwitched) {
      val withdrawalEpochNumberToRemove: Int = withdrawalEpochInfo.epoch - 2
      for (counter <- 0 to getWithdrawalEpochCounter(withdrawalEpochNumberToRemove)) {
        removeList.add(getWithdrawalRequestsKey(withdrawalEpochInfo.epoch - 2, counter))
      }
      removeList.add(getWithdrawalEpochCounterKey(withdrawalEpochNumberToRemove))
    }

    // Update Certificate related data
    withdrawalEpochCertificateOpt.map { withdrawalEpochCertificate =>
      updateList.add(new JPair(getWithdrawalBlockKey(withdrawalEpochInfo.epoch - 1), version))
      updateList.add(new JPair(lastWithdrawalCertificatePreviousMcBlockHashKey, withdrawalEpochCertificate.endEpochBlockHash))
    }

    // Update Consensus related data
    if(getConsensusEpochNumber.getOrElse(intToConsensusEpochNumber(0)) != consensusEpoch) {
      updateList.add(new JPair(consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(consensusEpoch))))
    }

    storage.update(version, updateList, removeList)

    this
  }

  def lastVersionId : Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
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
