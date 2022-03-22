package com.horizen.helper

import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction

trait TransactionSubmitProvider {

  @throws(classOf[IllegalArgumentException])
  def submitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]]): Unit

  def asyncSubmitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]],
                        callback:(Boolean, Option[Throwable]) => Unit): Unit
}
