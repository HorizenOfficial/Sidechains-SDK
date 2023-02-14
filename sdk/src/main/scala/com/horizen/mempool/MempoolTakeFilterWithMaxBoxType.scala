package com.horizen.mempool
import com.horizen.SidechainTypes
import com.horizen.box.Box
import scala.collection.JavaConverters._

class MempoolTakeFilterWithMaxBoxType[B <: Box[_]](maxCount: Int) extends MempoolTakeFilter {

  var totCount = 0;

  override def evaluateTx(tx: SidechainTypes#SCBT): Boolean = {
    val boxesOfTypes = tx.newBoxes().asScala.count(box => box.isInstanceOf[B])
    totCount + boxesOfTypes <= maxCount
  }

  override def accumulateTx(tx: SidechainTypes#SCBT): Unit = {
    val boxesOfTypes = tx.newBoxes().asScala.count(box => box.isInstanceOf[B])
    totCount = totCount + boxesOfTypes
  }
}
