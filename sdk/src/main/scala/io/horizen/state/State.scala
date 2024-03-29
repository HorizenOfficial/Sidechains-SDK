package io.horizen.state

import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.{AbstractState, SidechainTypes}
import io.horizen.transaction.Transaction
import sparkz.core.PersistentNodeViewModifier
import sparkz.core.transaction.state.StateReader

trait State[TX <: Transaction, PMOD <: PersistentNodeViewModifier, SV <: StateView[TX], S <: State[TX, PMOD, SV, S]]
  extends AbstractState[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, S]
    with BaseStateReader
    with StateReader {
  self: S =>

  def getView: SV
}
