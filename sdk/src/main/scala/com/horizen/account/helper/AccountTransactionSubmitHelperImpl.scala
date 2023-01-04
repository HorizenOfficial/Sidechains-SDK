package com.horizen.account.helper

import com.google.inject.{Inject, Provider}
import com.horizen.SidechainTypes
import com.horizen.account.AccountSidechainApp

import java.lang
import java.util.Optional
import java.util.function.BiConsumer
import scala.compat.java8.OptionConverters.RichOptionForJava8

class AccountTransactionSubmitHelperImpl @Inject()(val appProvider: Provider[AccountSidechainApp]) extends AccountTransactionSubmitHelper {

  @throws(classOf[IllegalArgumentException])
  override def submitTransaction(tx: SidechainTypes#SCAT): Unit = {
    appProvider.get().getTransactionSubmitProvider.submitTransaction(tx)
  }

  override def asyncSubmitTransaction(tx: SidechainTypes#SCAT, callback: BiConsumer[lang.Boolean, Optional[Throwable]]): Unit = {
    appProvider.get().getTransactionSubmitProvider.asyncSubmitTransaction(tx, (res, t) => callback.accept(res, t.asJava))
  }
}