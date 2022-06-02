package com.horizen.state

import com.horizen.transaction.Transaction
import scorex.core.PersistentNodeViewModifier
import scorex.core.transaction.state.MinimalState

trait State[TX <: Transaction, PMOD <: PersistentNodeViewModifier, SV <: StateView[TX, SV], S <: State[TX, PMOD, SV, S]]
  extends MinimalState[PMOD, S]
    with StateReader {
  self: S =>

  // MinimalState:
  // def applyModifier(mod: M): Try[MS]
  // def rollbackTo(version: VersionTag): Try[MS]
  // def getReader: StateReader = this

  def getView: SV
}
