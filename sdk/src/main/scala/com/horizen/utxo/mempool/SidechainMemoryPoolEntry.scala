package com.horizen.utxo.mempool

import com.horizen.SidechainTypes
import com.horizen.utils.FeeRate

case class SidechainMemoryPoolEntry(unconfirmedTx: SidechainTypes#SCBT) {
  def getUnconfirmedTx(): SidechainTypes#SCBT = {
    unconfirmedTx
  }

  lazy val feeRate: FeeRate = new FeeRate(unconfirmedTx.fee(), unconfirmedTx.size())

}
