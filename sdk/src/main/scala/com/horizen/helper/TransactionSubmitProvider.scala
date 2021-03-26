package com.horizen.helper

import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction

trait TransactionSubmitProvider {

  def submitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]],
                        callback:(Boolean, Option[Throwable]) => Unit) : Unit
}
