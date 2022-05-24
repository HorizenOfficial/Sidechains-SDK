package com.horizen.helper

import com.google.inject.{Inject, Provider}
import com.horizen.SidechainApp
import com.horizen.account.AccountSidechainApp
import com.horizen.account.transaction.AccountTransaction
import com.horizen.box.Box
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction

import java.lang
import java.util.Optional
import java.util.function.BiConsumer
import scala.compat.java8.OptionConverters.RichOptionForJava8

class AccountTransactionSubmitHelperImpl @Inject()(val appProvider: Provider[AccountSidechainApp]) extends AccountTransactionSubmitHelper {

  @throws(classOf[IllegalArgumentException])
  override def submitTransaction(tx: AccountTransaction[Proposition, Proof[Proposition]]): Unit = {
    appProvider.get().getTransactionSubmitProvider.submitTransaction(tx)
  }

  override def asyncSubmitTransaction(tx: AccountTransaction[Proposition, Proof[Proposition]], callback: BiConsumer[lang.Boolean, Optional[Throwable]]): Unit = {
    appProvider.get().getTransactionSubmitProvider.asyncSubmitTransaction(tx, (res, t) => callback.accept(res, t.asJava))
  }
}