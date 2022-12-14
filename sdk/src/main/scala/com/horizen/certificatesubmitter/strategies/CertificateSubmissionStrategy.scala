package com.horizen.certificatesubmitter.strategies

import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import com.horizen.{AbstractHistory, AbstractState}
import scorex.util.ModifierId
import sparkz.core.NodeViewHolder.CurrentView

trait CertificateSubmissionStrategy {

  def getStatus[H <: AbstractHistory[_, _, _, _, _, _], S <: AbstractState[_, _, _, _]](sidechainNodeView: CurrentView[H, S, _, _], id: ModifierId): SubmissionWindowStatus

  def checkQuality(status: SignaturesStatus): Boolean

}
