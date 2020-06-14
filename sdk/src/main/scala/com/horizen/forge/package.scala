package com.horizen

import scorex.core.NodeViewHolder.CurrentView

package object forge {
  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

}
