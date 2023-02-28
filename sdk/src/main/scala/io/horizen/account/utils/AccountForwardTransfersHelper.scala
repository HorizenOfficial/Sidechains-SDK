package io.horizen.account.utils

import io.horizen.account.block.AccountBlock
import io.horizen.transaction.mainchain.ForwardTransfer

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`

object AccountForwardTransfersHelper {
  def getForwardTransfersForBlock(block: AccountBlock): Seq[ForwardTransfer] = {
    block.mainchainBlockReferencesData.flatMap(mcBlockRefData =>
      mcBlockRefData.sidechainRelatedAggregatedTransaction
        .map(_.mc2scTransactionsOutputs
          .withFilter(_.isInstanceOf[ForwardTransfer])
          .map(_.asInstanceOf[ForwardTransfer]))
        .getOrElse(Seq.empty)
    )
  }
}
