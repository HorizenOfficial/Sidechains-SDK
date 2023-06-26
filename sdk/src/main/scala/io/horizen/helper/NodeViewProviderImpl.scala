package io.horizen.helper

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import io.horizen.AbstractSidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import io.horizen.{SidechainNodeViewBase, SidechainTypes}
import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.node.{NodeHistoryBase, NodeMemoryPoolBase, NodeStateBase, NodeWalletBase}
import io.horizen.transaction.Transaction
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet, SidechainNodeView}

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

