package io.horizen.consensus

import sparkz.core.utils.TimeProvider
import sparkz.core.utils.TimeProvider.Time

trait TimeProviderFixture {
  val timeProvider = new TimeProvider {
    override def time(): Time = System.currentTimeMillis()
  }
}
