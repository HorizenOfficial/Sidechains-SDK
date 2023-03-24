package io.horizen.helper

import io.horizen.SidechainNodeViewBase
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import io.horizen.transaction.Transaction

trait NodeViewProvider[TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PMOD, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PMOD, FPI, NH, NS, NW, NP]] {

  def getNodeView(view: NV => Unit)

}

