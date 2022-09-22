package com.horizen.certificatesubmitter.submitters

import akka.actor.ActorRef
import com.horizen.SidechainSettings
import com.horizen.certificatesubmitter.CertificateSubmitter
import com.horizen.params.NetworkParams
import com.horizen.websocket.client.MainchainNodeChannel

import scala.concurrent.ExecutionContext

class ThresholdSigCircuitSubmitterWithKeyRotation(settings: SidechainSettings,
                                                  sidechainNodeViewHolderRef: ActorRef,
                                                  params: NetworkParams,
                                                  mainchainChannel: MainchainNodeChannel)
                                                 (implicit ec: ExecutionContext)
  extends CertificateSubmitter(settings, sidechainNodeViewHolderRef, params: NetworkParams, mainchainChannel){

}
