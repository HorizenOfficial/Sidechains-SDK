package com.horizen

import com.horizen.utils.FeeRate

case class SidechainMemoryPoolEntry(unconfirmedTx: SidechainTypes#SCBT, txFeeRate: FeeRate) {
  def getUnconfirmedTx(): SidechainTypes#SCBT = {
    unconfirmedTx
  }

  def getTxFeeRate(): FeeRate = {
    txFeeRate
  }
}
