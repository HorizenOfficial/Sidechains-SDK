package com.horizen.consensus

import scorex.core.utils.TimeProvider
import scorex.core.utils.TimeProvider.Time

trait TimeProviderFixture {
  val timeProvider = new TimeProvider {
    override def time(): Time = System.currentTimeMillis()
  }
}
