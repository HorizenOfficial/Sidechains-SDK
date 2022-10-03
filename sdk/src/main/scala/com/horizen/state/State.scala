package com.horizen.state

import com.horizen.transaction.Transaction
import sparkz.core.PersistentNodeViewModifier
import sparkz.core.transaction.state.{MinimalState, StateReader}

trait State[TX <: Transaction, PMOD <: PersistentNodeViewModifier, SV <: StateView[TX], S <: State[TX, PMOD, SV, S]]
  extends MinimalState[PMOD, S]
    with BaseStateReader
    with StateReader {
  self: S =>

  def getView: SV
}
