package io.horizen

import io.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.transaction.Transaction
import sparkz.core.network.NodeViewSynchronizer.ReceivableMessages.FailedTransaction

/* This trait  disables NodeViewHolder methods that should not be used in a Seeder node, i.e. a node where transactions
 handling is disabled */
trait NodeViewHolderForSeederNode[TX <: Transaction, H <: SidechainBlockHeaderBase, PMOD <: SidechainBlockBase[TX, H]]
  extends AbstractSidechainNodeViewHolder[TX, H, PMOD] {

  override protected def applyLocallyGeneratedTransactions(newTxs: Iterable[TX]): Unit = {
    newTxs.foreach { tx =>
      context.system.eventStream.publish(
        FailedTransaction(
          tx.id,
          new Exception("Transactions handling disabled"),
          immediateFailure = false // This won't penalize the sender, because there can be legacy nodes that don't support seeder nodes as peer
        )
      )
    }
  }

  override protected def updateMemPool(blocksRemoved: Seq[PMOD], blocksApplied: Seq[PMOD], memPool: MP, state: MS): MP = memPool
}