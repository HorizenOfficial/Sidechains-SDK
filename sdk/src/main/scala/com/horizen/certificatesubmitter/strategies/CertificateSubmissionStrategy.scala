package com.horizen.certificatesubmitter.strategies

import com.horizen.block.SidechainBlock
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import sparkz.core.NodeViewHolder.CurrentView

trait CertificateSubmissionStrategy {
  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  def getStatus(sidechainNodeView: View, block: SidechainBlock): SubmissionWindowStatus
  def checkQuality(status: SignaturesStatus): Boolean

}
