package com.horizen.state

import com.horizen.block.SidechainBlock
import com.horizen.transaction.Transaction
import scorex.core.transaction.state.MinimalState

trait State[TX <: Transaction, SV <: StateView[TX, SV], S <: State[TX, SV, S]]
  extends MinimalState[SidechainBlock, S]
    with StateReader {
  self: S =>
  override type NVCT = this.type

  // MinimalState:
  // def applyModifier(mod: M): Try[MS]
  // def rollbackTo(version: VersionTag): Try[MS]
  // def getReader: StateReader = this

  def getView: SV
}
