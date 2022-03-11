package com.horizen.utils

import com.horizen.block.SidechainBlock
import com.horizen.transaction.mainchain.SidechainCreation

import scala.util.Try

object BlockUtils {
  def tryGetSidechainCreation(block: SidechainBlock): Try[SidechainCreation] = Try {
    block.mainchainBlockReferencesData.head
      .sidechainRelatedAggregatedTransaction.get
      .mc2scTransactionsOutputs.get(0).asInstanceOf[SidechainCreation]
  }
}
