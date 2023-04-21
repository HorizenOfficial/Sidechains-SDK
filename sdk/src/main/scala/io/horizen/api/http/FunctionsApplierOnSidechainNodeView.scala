package io.horizen.api.http

import io.horizen.SidechainNodeViewBase
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import io.horizen.transaction.Transaction

trait FunctionsApplierOnSidechainNodeView [
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  S <: NodeStateBase,
  W <: NodeWalletBase,
  P <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PM, FPI, NH, S, W, P]] {
  def applyFunctionOnSidechainNodeView[R](f: java.util.function.Function[NV, R]): R
  def applyBiFunctionOnSidechainNodeView[T, R](f: java.util.function.BiFunction[NV, T, R], parameter: T): R
}
