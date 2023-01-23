package com.horizen

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import com.horizen.transaction.Transaction

trait SidechainNodeViewBase[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PM, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX]] {
  def getNodeHistory: NH

  def getNodeState: NS

  def getNodeMemoryPool: NP

  def getNodeWallet: NW

}