package com.horizen.utxo.helper

import com.google.inject.{Inject, Provider}
import com.horizen.utxo.SidechainApp
import com.horizen.utxo.node.SidechainNodeView

import java.util.function.Consumer

class NodeViewHelperImpl @Inject()(val appProvider: Provider[SidechainApp]) extends NodeViewHelper {
  override def getNodeView(callback: Consumer[SidechainNodeView]): Unit = {
    appProvider.get().getNodeViewProvider.getNodeView(
      view => callback.accept(view)
    )
  }
}