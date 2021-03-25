package com.horizen.helper

import com.horizen.node.SidechainNodeView

trait NodeViewProvider {

  def getNodeView(view: SidechainNodeView => Unit)

}
