package com.horizen.history.validation

import sparkz.core.PersistentNodeViewModifier
import scala.util.Try

trait SemanticBlockValidator[PMOD <: PersistentNodeViewModifier] {
  def validate(block: PMOD): Try[Unit]
}
