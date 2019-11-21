package com.horizen.validation

import com.horizen.block.SidechainBlock

import scala.util.Try

trait SemanticBlockValidator {
  def validate(block: SidechainBlock): Try[Unit]
}
