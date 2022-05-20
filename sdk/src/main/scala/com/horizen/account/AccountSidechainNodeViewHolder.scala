package com.horizen.account

import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.wallet.AccountWallet
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainSettings, SidechainTypes}
import com.horizen.params.NetworkParams
import scorex.core.utils.NetworkTimeProvider

abstract class AccountSidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                                     params: NetworkParams,
                                     timeProvider: NetworkTimeProvider)
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCAT, AccountBlock](sidechainSettings, params, timeProvider) {

  override type VL = AccountWallet
  override type HIS = AccountHistory
  override type MS = AccountState
  override type MP = AccountMemoryPool

  override def restoreState(): Option[(HIS, MS, VL, MP)] = ???

  override protected def genesisState: (HIS, MS, VL, MP) = ???

  override protected def applyConsensusEpochInfo(history: HIS, state: MS, wallet: VL, modToApply: AccountBlock): (HIS, VL) = ???

  override protected def scanBlockWithFeePayments(history: HIS, state: MS, wallet: VL, modToApply: AccountBlock): (HIS, VL) = ???
}
