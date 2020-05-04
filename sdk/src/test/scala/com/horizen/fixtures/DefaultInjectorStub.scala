package com.horizen.fixtures

import com.google.inject.{Binder, Module}
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.horizen.companion.{SidechainBoxesDataCompanion, SidechainProofsCompanion}
import com.horizen.transaction.SidechainCoreTransactionFactory

import java.util.{HashMap => JHashMap}

class DefaultInjectorStub extends Module {
  override def configure(binder: Binder): Unit = {
    binder.bind(classOf[SidechainBoxesDataCompanion])
      .toInstance(SidechainBoxesDataCompanion(new JHashMap()))

    binder.bind(classOf[SidechainProofsCompanion])
      .toInstance(SidechainProofsCompanion(new JHashMap()))

    binder.install(new FactoryModuleBuilder()
      .build(classOf[SidechainCoreTransactionFactory]))
  }
}
