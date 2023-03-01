package io.horizen.helper

import io.horizen.transaction.Transaction

trait TransactionSubmitProvider[TX <: Transaction] {

  @throws(classOf[IllegalArgumentException])
  def submitTransaction(tx: TX): Unit

  def asyncSubmitTransaction(tx: TX,
                             callback:(Boolean, Option[Throwable]) => Unit): Unit
}
