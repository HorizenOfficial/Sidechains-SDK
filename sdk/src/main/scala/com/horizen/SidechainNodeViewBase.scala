package com.horizen

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import com.horizen.transaction.Transaction

trait SidechainNodeViewBase[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H],
  NH <: NodeHistoryBase[TX, H, PM],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX]] {
  def getNodeHistory: NH

  def getNodeState: NS

  def getNodeMemoryPool: NP

  def getNodeWallet: NW

}