package io.horizen.api.http

import com.horizen.utxo.node.SidechainNodeView

trait FunctionsApplierOnSidechainNodeView {
  def applyFunctionOnSidechainNodeView[R](f: java.util.function.Function[SidechainNodeView, R]): R
  def applyBiFunctionOnSidechainNodeView[T, R](f: java.util.function.BiFunction[SidechainNodeView, T, R], parameter: T): R
}
