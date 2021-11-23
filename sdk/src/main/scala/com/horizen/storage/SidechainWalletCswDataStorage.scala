package com.horizen.storage

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.SidechainTypes
import com.horizen.utils.{ByteArrayWrapper, CswData, CswDataSerializer, ListSerializer, Pair => JPair}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

import java.util.{ArrayList => JArrayList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}

class SidechainWalletCswDataStorage(storage: Storage) extends ScorexLogging with SidechainTypes {
  require(storage != null, "Storage must be NOT NULL.")

  private val cswDataListSerializer = new ListSerializer[CswData](CswDataSerializer)

  private[horizen] val withdrawalEpochKey = calculateKey("withdrawalEpoch".getBytes)

  private val undefinedWithdrawalEpochCounter: Int = -1

  private[horizen] def getWithdrawalEpochCounterKey(withdrawalEpoch: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("withdrawalEpochCounter".getBytes, Ints.toByteArray(withdrawalEpoch)))
  }

  private[horizen] def getCswDataKey(withdrawalEpoch: Int, counter: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("withdrawalRequests".getBytes, Ints.toByteArray(withdrawalEpoch), Ints.toByteArray(counter)))
  }

  private[horizen] def calculateKey(boxId: Array[Byte]): ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
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

  def getWithdrawalEpoch: Int = {
    storage.get(withdrawalEpochKey).asScala match {
      case Some(baw) => Ints.fromByteArray(baw.data)
      case None => 0 // Initial value
    }
  }

  /*
   * Returns the sequence of data to create CSW for every closed box existing in the end of given epoch
   * and for every FT created during this withdrawal epoch.
   * Note: for FT we have 2 records: one to withdraw it as FT, another - as UTXO.
   */
  def getCswData(withdrawalEpoch: Int): Seq[CswData] = {
    // Aggregate CSW data entries until reaching the counter, where the key is not present in the storage.
    val cswDataSeq: ListBuffer[CswData] = ListBuffer()
    val lastCounter: Int = getWithdrawalEpochCounter(withdrawalEpoch)
    for (counter <- 0 to lastCounter) {
      storage.get(getCswDataKey(withdrawalEpoch, counter)).asScala match {
        case Some(baw) =>
          cswDataListSerializer.parseBytesTry(baw.data) match {
            case Success(list) =>
              cswDataSeq.appendAll(list.asScala)
            case Failure(exception) =>
              throw new IllegalStateException("Error while csw data parsing.", exception)
          }
        case None =>
          throw new IllegalStateException("Error while csw data retrieving: record is missing.")
      }
    }
    cswDataSeq
  }

  def update(version: ByteArrayWrapper,
             withdrawalEpoch: Int,
             cswData: Seq[CswData]): Try[SidechainWalletCswDataStorage] = Try {

    val removeList: JArrayList[ByteArrayWrapper] = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    // If withdrawal epoch switched to the next one, then:
    // remove outdated CSW records and counters (4 epochs before);
    val currentWithdrawalEpoch = getWithdrawalEpoch
    val isEpochSwitched: Boolean = withdrawalEpoch != currentWithdrawalEpoch
    if(isEpochSwitched) {
      val epochToRemove: Int = withdrawalEpoch - 4
      for (counter <- 0 to getWithdrawalEpochCounter(epochToRemove)) {
        removeList.add(getCswDataKey(epochToRemove, counter))
      }
      removeList.add(getWithdrawalEpochCounterKey(epochToRemove))
    }

    // Update withdrawal epoch number if changed
    if(isEpochSwitched) {
      updateList.add(new JPair(withdrawalEpochKey, new ByteArrayWrapper(Ints.toByteArray(withdrawalEpoch))))
    }

    // Add CSW data if present
    if (cswData.nonEmpty) {
      // Calculate the next counter for storing CSW data entries without duplication previously stored ones.
      val nextWithdrawalEpochCounter: Int = getWithdrawalEpochCounter(withdrawalEpoch) + 1
      // Update counter
      updateList.add(new JPair(getWithdrawalEpochCounterKey(withdrawalEpoch),
        new ByteArrayWrapper(Ints.toByteArray(nextWithdrawalEpochCounter))))
      // Add CSW data
      updateList.add(new JPair(getCswDataKey(withdrawalEpoch, nextWithdrawalEpochCounter),
        new ByteArrayWrapper(cswDataListSerializer.toBytes(cswData.asJava))))
    }

    storage.update(version, updateList, removeList)
    this
  }

  def lastVersionId: Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions: Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback(version: ByteArrayWrapper): Try[SidechainWalletCswDataStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}
