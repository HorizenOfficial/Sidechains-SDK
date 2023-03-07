package io.horizen.account.state

import io.horizen.SidechainTypes


trait AccountEventNotifier {
  def sendNewExecTxsEvent(listOfNewExecTxs: Iterable[SidechainTypes#SCAT])
}

trait AccountEventNotifierProvider {
  def getEventNotifier(): AccountEventNotifier
}

