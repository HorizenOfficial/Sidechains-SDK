package com.horizen.fixtures

import com.google.inject.{Binder, Module}
import com.horizen.companion.{SidechainBoxesDataCompanion, SidechainProofsCompanion}

import java.util.{HashMap => JHashMap}

class DefaultInjectorStub extends Module {
  override def configure(binder: Binder): Unit = {
    binder.bind(classOf[SidechainBoxesDataCompanion])
      .toInstance(SidechainBoxesDataCompanion(new JHashMap()))

    binder.bind(classOf[SidechainProofsCompanion])
      .toInstance(SidechainProofsCompanion(new JHashMap()))
  }
}
