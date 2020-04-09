package com.horizen.storage

import java.util

import com.google.common.primitives.Bytes
import com.google.common.primitives.Longs
import java.util.{Arrays, ArrayList => JArrayList, List => JList}

import com.google.common.primitives.Ints
import com.horizen.SidechainTypes
import com.horizen.block.MainchainBackwardTransferCertificate
import com.horizen.utils.{Pair => JPair}

import scala.util._
import scala.collection.JavaConverters._
import com.horizen.box.{Box, WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.proposition.Proposition
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ListSerializer, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

class SidechainStateStorage (storage : Storage, sidechainBoxesCompanion: SidechainBoxesCompanion)
  extends ScorexLogging
  with SidechainTypes
{
  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  private[horizen] val epochInformationKey = calculateKey("Epoch information".getBytes)

  private val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequestBox](WithdrawalRequestBoxSerializer.getSerializer)

  private[horizen] def getWithdrawalRequestsKey(epoch: Int) : ByteArrayWrapper = {
    calculateKey(Ints.toByteArray(epoch))
  }

  private[horizen] def getWithdrawalBlockKey(epoch: Int) : ByteArrayWrapper = {
    calculateKey(("Withdrawal block - " + epoch).getBytes)
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
    storage.get(epochInformationKey) match {
      case v if v.isPresent =>
        WithdrawalEpochInfoSerializer.parseBytesTry(v.get().data) match {
          case Success(withdrawalEpochInfo) => Option(withdrawalEpochInfo)
          case Failure(exception) =>
            log.error("Error while withdrawal epoch info information parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getWithdrawalRequests(epoch: Int) : Seq[WithdrawalRequestBox] = {
    storage.get(getWithdrawalRequestsKey(epoch)) match {
      case v if v.isPresent =>
        withdrawalRequestSerializer.parseBytesTry(v.get().data) match {
          case Success(withdrawalRequests) => withdrawalRequests.asScala
          case Failure(exception) =>
            log.error("Error while withdrawal requests parsing.", exception)
            Seq[WithdrawalRequestBox]()
        }
      case _ => Seq[WithdrawalRequestBox]()
    }
  }

  def getUnprocessedWithdrawalRequests(epoch: Int) : Option[Seq[WithdrawalRequestBox]] = {
    storage.get(getWithdrawalBlockKey(epoch)) match {
      case v if v.isPresent => None
      case _ => Some(getWithdrawalRequests(epoch))
    }

  }

  def update(version : ByteArrayWrapper, withdrawalEpochInfo: WithdrawalEpochInfo,
             boxUpdateList : Set[SidechainTypes#SCB],
             boxIdsRemoveList : Set[Array[Byte]],
             withdrawalRequestAppendList : Set[WithdrawalRequestBox],
             containsBackwardTransferCertificate: Boolean) : Try[SidechainStateStorage] = Try {
    require(withdrawalEpochInfo != null, "WithdrawalEpochInfo must be NOT NULL.")
    require(boxUpdateList != null, "List of Boxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveList != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    require(!boxUpdateList.contains(null), "Box to add/update must be NOT NULL.")
    require(!boxIdsRemoveList.contains(null), "BoxId to remove must be NOT NULL.")
    require(withdrawalRequestAppendList != null, "List of WithdrawalRequests to append must be NOT NULL. Use empty List instead.")
    require(!withdrawalRequestAppendList.contains(null), "WithdrawalRequest to append must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    for (r <- boxIdsRemoveList)
      removeList.add(calculateKey(r))

    for (b <- boxUpdateList)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](calculateKey(b.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))))

    updateList.add(new JPair(epochInformationKey,
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfo))))

    if (withdrawalRequestAppendList.nonEmpty) {
      val withdrawalRequestList = getWithdrawalRequests(withdrawalEpochInfo.epoch) ++ withdrawalRequestAppendList

      updateList.add(new JPair(getWithdrawalRequestsKey(withdrawalEpochInfo.epoch),
        new ByteArrayWrapper(withdrawalRequestSerializer.toBytes(withdrawalRequestList.asJava))))

    }

    if (containsBackwardTransferCertificate)
      updateList.add(new JPair(getWithdrawalBlockKey(withdrawalEpochInfo.epoch - 1),
        version))

    storage.update(version,
      updateList,
      removeList)

    this
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
