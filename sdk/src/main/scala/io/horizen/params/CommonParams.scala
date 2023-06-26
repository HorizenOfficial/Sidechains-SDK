package io.horizen.params

import io.horizen.utils.Utils
import sparkz.core.NodeViewModifier

object CommonParams {
  val mainchainBlockHashLength: Int = Utils.SHA256_LENGTH
  val sidechainIdLength: Int = NodeViewModifier.ModifierIdSize
  val mainchainTransactionHashLength: Int = Utils.SHA256_LENGTH
}
