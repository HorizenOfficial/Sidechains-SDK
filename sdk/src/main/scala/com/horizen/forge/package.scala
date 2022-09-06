package com.horizen

import sparkz.core.NodeViewHolder.CurrentView

package object forge {
  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

}
