package com.horizen.helper

import com.horizen.account.transaction.AccountTransaction
import com.horizen.box.Box
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.transaction.{BoxTransaction, Transaction}

trait TransactionSubmitProviderBase[TX <: Transaction] {

  @throws(classOf[IllegalArgumentException])
  def submitTransaction(tx: TX): Unit

  def asyncSubmitTransaction(tx: TX,
                             callback:(Boolean, Option[Throwable]) => Unit): Unit
}

trait TransactionSubmitProvider extends TransactionSubmitProviderBase[BoxTransaction[Proposition, Box[Proposition]]] {

  @throws(classOf[IllegalArgumentException])
  def submitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]]): Unit

  def asyncSubmitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]],
                             callback:(Boolean, Option[Throwable]) => Unit): Unit
}

trait AccountTransactionSubmitProvider extends TransactionSubmitProviderBase[AccountTransaction[Proposition, Proof[Proposition]]] {

  @throws(classOf[IllegalArgumentException])
  def submitTransaction(tx: AccountTransaction[Proposition, Proof[Proposition]]): Unit

  def asyncSubmitTransaction(tx: AccountTransaction[Proposition, Proof[Proposition]],
                             callback:(Boolean, Option[Throwable]) => Unit): Unit
}