package io.horizen.utxo.helper

import com.google.inject.{Inject, Provider}
import io.horizen.utxo.SidechainApp
import io.horizen.utxo.node.SidechainNodeView

import java.util.function.Consumer

class NodeViewHelperImpl @Inject()(val appProvider: Provider[SidechainApp]) extends NodeViewHelper {
  override def getNodeView(callback: Consumer[SidechainNodeView]): Unit = {
    appProvider.get().getNodeViewProvider.getNodeView(
      view => callback.accept(view)
    )
  }
}