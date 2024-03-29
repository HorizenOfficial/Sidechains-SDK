package io.horizen.certificatesubmitter.strategies

import io.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import io.horizen.AbstractState
import io.horizen.history.AbstractHistory
import sparkz.util.ModifierId
import sparkz.core.NodeViewHolder.CurrentView

trait CertificateSubmissionStrategy {

  def getStatus[H <: AbstractHistory[_, _, _, _, _, _], S <: AbstractState[_, _, _, _]](history: H, state: S, id: ModifierId): SubmissionWindowStatus

  def checkQuality(status: SignaturesStatus): Boolean

}
