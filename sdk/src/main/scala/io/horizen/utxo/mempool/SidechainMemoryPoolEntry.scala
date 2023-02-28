package io.horizen.utxo.mempool

import io.horizen.SidechainTypes
import io.horizen.utils.FeeRate

case class SidechainMemoryPoolEntry(unconfirmedTx: SidechainTypes#SCBT) {
  def getUnconfirmedTx(): SidechainTypes#SCBT = {
    unconfirmedTx
  }

  lazy val feeRate: FeeRate = new FeeRate(unconfirmedTx.fee(), unconfirmedTx.size())

}
