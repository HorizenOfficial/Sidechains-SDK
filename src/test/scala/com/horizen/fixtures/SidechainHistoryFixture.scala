package com.horizen.fixtures

import com.horizen.SidechainHistory

trait SidechainHistoryFixture {

  def getSidechainHistory : SidechainHistory = {
    new SidechainHistory()
  }

}
