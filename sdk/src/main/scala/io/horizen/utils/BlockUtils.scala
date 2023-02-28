package io.horizen.utils

import com.horizen.block.{SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.transaction.mainchain.SidechainCreation
import scala.util.Try

object BlockUtils {
  def tryGetSidechainCreation(block: SidechainBlockBase[_, _ <: SidechainBlockHeaderBase]): Try[SidechainCreation] = Try {
    block.mainchainBlockReferencesData.head
      .sidechainRelatedAggregatedTransaction.get
      .mc2scTransactionsOutputs.get(0).asInstanceOf[SidechainCreation]
  }
}
