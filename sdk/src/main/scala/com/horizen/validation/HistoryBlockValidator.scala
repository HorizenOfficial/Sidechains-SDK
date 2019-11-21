package com.horizen.validation

import com.horizen.SidechainHistory
import com.horizen.block.SidechainBlock

import scala.util.Try

trait HistoryBlockValidator {
  def validate(block: SidechainBlock, history: SidechainHistory): Try[Unit]
}
