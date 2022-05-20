package com.horizen.account

import com.horizen.account.block.SidechainAccountBlock
import com.horizen.account.mempool.SidechainAccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.wallet.AccountWallet
import com.horizen.{AbstractSidechainNodeViewHolder, SidechainSettings, SidechainTypes}
import com.horizen.params.NetworkParams
import scorex.core.utils.NetworkTimeProvider

abstract class AccountSidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                                     params: NetworkParams,
                                     timeProvider: NetworkTimeProvider)
  extends AbstractSidechainNodeViewHolder[SidechainTypes#SCAT, SidechainAccountBlock](sidechainSettings, params, timeProvider) {

  override type VL = AccountWallet
  // TODO: override type HIS = SidechainHistory // define history
  override type MS = AccountState
  override type MP = SidechainAccountMemoryPool

  override def restoreState(): Option[(HIS, MS, VL, MP)] = ???

  override protected def genesisState: (HIS, MS, VL, MP) = ???

  override protected def applyConsensusEpochInfo(history: HIS, state: MS, wallet: VL, modToApply: SidechainAccountBlock): (HIS, VL) = ???

  override protected def scanBlockWithFeePayments(history: HIS, state: MS, wallet: VL, modToApply: SidechainAccountBlock): (HIS, VL) = ???
}
