package com.horizen.account.api.rpc.service.utils

import com.horizen.account.block.AccountBlock
import com.horizen.account.forger.AccountForgeMessageBuilder
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state._
import com.horizen.account.wallet.AccountWallet
import com.horizen.chain.SidechainBlockInfo
import com.horizen.params.NetworkParams
import com.horizen.utils.{ClosableResourceHandler, WithdrawalEpochUtils}
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.consensus.ModifierSemanticValidity

class PendingBlock(
    nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]
) extends ScorexLogging with ClosableResourceHandler {

  type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]
  private val networkParams: NetworkParams = nodeView.state.params

  def getPendingBlock: Option[AccountBlock] = {
    val transactions = nodeView.pool.getExecutableTransactionsMap.values.flatMap(_.values)
    new AccountForgeMessageBuilder(null, null, networkParams, false)
      .getPendingBlock(
        nodeView,
        transactions
      )
  }

  def getBlockInfo(block: AccountBlock, parentId: ModifierId, parentInfo: SidechainBlockInfo): SidechainBlockInfo = {
    new SidechainBlockInfo(
      parentInfo.height + 1,
      parentInfo.score + 1,
      parentId,
      System.currentTimeMillis / 1000,
      ModifierSemanticValidity.Unknown,
      null,
      null,
      WithdrawalEpochUtils.getWithdrawalEpochInfo(
        block,
        parentInfo.withdrawalEpochInfo,
        networkParams
      ),
      None,
      parentInfo.lastBlockInPreviousConsensusEpoch
    )
  }
}
