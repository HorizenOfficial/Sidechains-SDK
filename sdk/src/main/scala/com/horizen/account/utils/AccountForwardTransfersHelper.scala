package com.horizen.account.utils

import com.horizen.account.block.AccountBlock
import com.horizen.transaction.mainchain.ForwardTransfer

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`

object AccountForwardTransfersHelper {
  def getForwardTransfersForBlock(block: AccountBlock): Seq[ForwardTransfer] = {
    block.mainchainBlockReferencesData.flatMap(mcBlockRefData =>
      mcBlockRefData.sidechainRelatedAggregatedTransaction match {
        case Some(tx) => tx.mc2scTransactionsOutputs().filter(
          _.isInstanceOf[ForwardTransfer]
        ).map(_.asInstanceOf[ForwardTransfer])
      }
    )
  }
}
