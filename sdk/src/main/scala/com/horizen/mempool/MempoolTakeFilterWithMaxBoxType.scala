package com.horizen.mempool
import com.horizen.SidechainTypes
import com.horizen.box.Box
import scala.collection.JavaConverters._

/**
 * A filter that accoumulate txs until the number of output boxes created by them reaches a limit
 */
class MempoolTakeFilterWithMaxBoxType[B <: Box[_]](boxClass: Class[B], maxCount: Int) extends MempoolTakeFilter {

  var totCount = 0;

  override def evaluateTx(tx: SidechainTypes#SCBT): Boolean = {
    val boxesOfTypes = tx.newBoxes().asScala.count(box => box.getClass.isAssignableFrom(boxClass))
    totCount + boxesOfTypes <= maxCount
  }

  override def accumulateTx(tx: SidechainTypes#SCBT): Unit = {
    val boxesOfTypes = tx.newBoxes().asScala.count(box => box.getClass.isAssignableFrom(boxClass))
    totCount = totCount + boxesOfTypes
  }
}