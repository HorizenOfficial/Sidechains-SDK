package com.horizen.account.helper

import com.google.inject.{Inject, Provider}
import com.horizen.account.AccountSidechainApp
import com.horizen.account.node.AccountNodeView

import java.util.function.Consumer

class AccountNodeViewHelperImpl @Inject()(val appProvider: Provider[AccountSidechainApp]) extends AccountNodeViewHelper {
  override def getNodeView(callback: Consumer[AccountNodeView]): Unit = {
    appProvider.get().getNodeViewProvider.getNodeView(
      view => callback.accept(view)
    )
  }
}