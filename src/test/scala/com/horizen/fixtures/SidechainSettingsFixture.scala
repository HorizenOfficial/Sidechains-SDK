package com.horizen.fixtures

import com.horizen.SidechainSettings

trait SidechainSettingsFixture {

  def getSidechainSettings : SidechainSettings = {
    new SidechainSettings(null, null, null)
  }

}
