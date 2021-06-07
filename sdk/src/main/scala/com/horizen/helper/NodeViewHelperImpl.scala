package com.horizen.helper

import java.util.function.Consumer
import com.google.inject.{Inject, Provider}
import com.horizen.node.SidechainNodeView
import com.horizen.SidechainApp

class NodeViewHelperImpl @Inject()(val appProvider: Provider[SidechainApp]) extends NodeViewHelper {
  override def getNodeView(callback: Consumer[SidechainNodeView]): Unit = {
    appProvider.get().getNodeViewProvider.getNodeView(
      view => callback.accept(view)
    )
  }
}