package com.horizen.helper

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.{SidechainNodeViewBase, SidechainTypes}
import com.horizen.block.{SidechainBlock, SidechainBlockBase, SidechainBlockHeader, SidechainBlockHeaderBase}
import com.horizen.chain.{AbstractFeePaymentsInfo, SidechainFeePaymentsInfo}
import com.horizen.node.{NodeHistory, NodeHistoryBase, NodeMemoryPool, NodeMemoryPoolBase, NodeState, NodeStateBase, NodeWallet, NodeWalletBase, SidechainNodeView}
import com.horizen.transaction.Transaction

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class NodeViewProviderImpl[TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  NH <: NodeHistoryBase[TX, H, PMOD, FPI],
  NS <: NodeStateBase,
  NW <: NodeWalletBase,
  NP <: NodeMemoryPoolBase[TX],
  NV <: SidechainNodeViewBase[TX, H, PMOD, FPI, NH, NS, NW, NP]](var nodeViewActor: ActorRef) extends  NodeViewProvider[TX, H, PMOD, FPI, NH, NS, NW, NP, NV] {

  implicit val duration: Timeout = 20 seconds

  override def getNodeView(f: NV => Unit): Unit = {
    nodeViewActor ?  GetDataFromCurrentSidechainNodeView(f)
  }
}

