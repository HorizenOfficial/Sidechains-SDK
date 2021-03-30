package com.horizen.helper

import java.lang
import java.util.Optional
import java.util.function.BiConsumer

import com.google.inject.{Inject, Provider}
import com.horizen.SidechainApp
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.transaction.BoxTransaction
import scala.compat.java8.OptionConverters.RichOptionForJava8

class TransactionSubmitHelperImpl @Inject()(val appProvider: Provider[SidechainApp]) extends TransactionSubmitHelper {
  override def submitTransaction(tx: BoxTransaction[Proposition, Box[Proposition]], callback: BiConsumer[lang.Boolean, Optional[Throwable]]): Unit = {
    appProvider.get().getTransactionSubmitProvider().submitTransaction(tx, (res, t) => callback.accept(res, t.asJava));
  }
}