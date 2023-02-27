package com.horizen.fixtures

import com.horizen.utxo.SidechainHistory

trait SidechainHistoryFixture {

  def getSidechainHistory : SidechainHistory = {
    null//new SidechainHistory()
  }

}
