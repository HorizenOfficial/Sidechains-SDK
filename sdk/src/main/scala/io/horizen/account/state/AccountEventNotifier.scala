package io.horizen.account.state

import io.horizen.SidechainTypes


trait AccountEventNotifier {
  def sendNewExecTxsEvent(listOfNewExecTxs: Iterable[SidechainTypes#SCAT]): Unit
}

trait AccountEventNotifierProvider {
  def getEventNotifier(): AccountEventNotifier
}

