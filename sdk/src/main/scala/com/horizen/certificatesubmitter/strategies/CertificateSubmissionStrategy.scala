package com.horizen.certificatesubmitter.strategies

import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.SignaturesStatus
import com.horizen.AbstractState
import com.horizen.history.AbstractHistory
import sparkz.util.ModifierId
import sparkz.core.NodeViewHolder.CurrentView

trait CertificateSubmissionStrategy {

  def getStatus[H <: AbstractHistory[_, _, _, _, _, _], S <: AbstractState[_, _, _, _]](history: H, state: S, id: ModifierId): SubmissionWindowStatus

  def checkQuality(status: SignaturesStatus): Boolean

}
