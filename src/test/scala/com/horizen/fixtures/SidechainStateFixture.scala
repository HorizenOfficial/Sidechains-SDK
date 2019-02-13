package com.horizen.fixtures

import com.horizen.SidechainState

trait SidechainStateFixture {

  def getSidechainState : SidechainState = {
    new SidechainState(null, null, null)
  }

}
