package com.horizen.helper

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.{SidechainNodeViewBase, SidechainTypes}
import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import com.horizen.transaction.Transaction
import com.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}

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

