package com.horizen.storage.performance

import com.horizen.storage.performance.Measure.OperationType


object Measure {
  sealed abstract class OperationType(val description: String) {
    override def toString: String = description
  }

  case object StorageCreation extends OperationType("Creation")
  case object StorageReadOnce extends OperationType("Read once")
  case object StorageReadBatching extends OperationType("Read batch")
  case object StorageWriteBatching extends OperationType("Write batch")
}


case class TimeMeasure(opType: OperationType,
                       totalTimeInNanoSec: Long,
                       itemsCount: Long,
                       itemsSizeInBytes: Long,
                       minimumBatchingSize: Long,
                       maximumBatchingSize: Long,
                       storageSize: Long) {
  def timeInSec: Double = totalTimeInNanoSec.asInstanceOf[Double] / 1000000000
  def timePer1000Item: Double = (timeInSec / itemsCount) * 1000
  def averageSizeInKB: Double = (itemsSizeInBytes.asInstanceOf[Double] / itemsCount) / 1024

  def +(that: TimeMeasure): TimeMeasure = {
    require(this.opType == that.opType)
    TimeMeasure(this.opType,
      totalTimeInNanoSec + that.totalTimeInNanoSec,
      itemsCount + that.itemsCount,
      itemsSizeInBytes + that.itemsSizeInBytes,
      Math.min(minimumBatchingSize, that.minimumBatchingSize),
      Math.max(maximumBatchingSize, that.maximumBatchingSize),
      Math.max(storageSize, that.storageSize))
  }


  private def formatString(str: String, size: Int): String = {
    val requiredSpaces = size - str.length
    if (requiredSpaces < 0) str
    else str.concat(Array.fill(requiredSpaces)(' ').mkString)
  }

  private lazy val opTypeString = formatString(f"${opType}", TimeMeasure.headerLen.head)
  private lazy val storageSizeString = formatString(f"${storageSize}", TimeMeasure.headerLen(1))
  private lazy val itemCountString = formatString(f"${itemsCount}", TimeMeasure.headerLen(2))
  private lazy val averageSizeInKbString = formatString(f"${averageSizeInKB}%.2f", TimeMeasure.headerLen(3))
  private lazy val minimumBatchingSizeString = formatString(f"${minimumBatchingSize / 1024.0}%.2f", TimeMeasure.headerLen(4))
  private lazy val maximumBatchingSizeString = formatString(f"${maximumBatchingSize / 1024.0}%.2f", TimeMeasure.headerLen(5))
  private lazy val timeFor1000ElemString = formatString(f"${timePer1000Item}%.2f", TimeMeasure.headerLen(6))
  private lazy val speedInKb = formatString(f"${(itemsSizeInBytes.asInstanceOf[Double] / timeInSec) / 1024}%.3f", TimeMeasure.headerLen(7))

  override def toString: String = {
    s"$opTypeString\t$storageSizeString\t$itemCountString\t$averageSizeInKbString\t$minimumBatchingSizeString\t$maximumBatchingSizeString\t$timeFor1000ElemString\t$speedInKb"
  }
}

object TimeMeasure {
  val header: Seq[String] = Seq("Operation Type",
    "Storage size",
    "Test elements count",
    "Element average size(KB)",
    "Minimum Batching Size(KB)",
    "Maximum Batching Size(KB)",
    "1000 element processing time(sec)",
    "Average Speed(KB/sec)")
  val headerLen: Seq[Int] = header.map(_.length)

  def measureStatistic(measured: Seq[TimeMeasure]): TimeMeasure = {
    require(measured.nonEmpty)
    measured.foldLeft(TimeMeasure(measured.head.opType, 0, 0, 0, Long.MaxValue, 0, 0)) ({_ + _})
  }
}