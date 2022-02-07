package com.horizen

import com.horizen.utils.Utils
import scorex.core.NodeViewModifier

object CommonParams {
  val mainchainBlockHashLength: Int = Utils.SHA256_LENGTH
  val sidechainIdLength: Int = NodeViewModifier.ModifierIdSize
  val mainchainTransactionHashLength: Int = Utils.SHA256_LENGTH
}
