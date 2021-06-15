package com.horizen.certificatesubmitter

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.horizen.SidechainSettings
import com.horizen.params.NetworkParams
import com.horizen.websocket.MainchainNodeChannel
import scorex.util.ScorexLogging

import scala.concurrent.ExecutionContext
import scala.util.Success

class CertificateSubmitterObserver() extends Actor with ScorexLogging {
  var certGenerationActiveState = false

  override def receive: Receive = {
    processStartProofGeneration orElse
    processStopProofGeneration orElse
    processGetProofGenerationState orElse {
      case message: Any => log.error(s"CertificateSubmitterObserver received strange message: ${message} from ${sender().path.name}")
    }
  }

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, CertificateSubmitter.StartProofGeneration.getClass)
    context.system.eventStream.subscribe(self, CertificateSubmitter.StopProofGeneration.getClass)
  }

  protected def processStartProofGeneration: Receive = {
    case CertificateSubmitter.StartProofGeneration => {
      certGenerationActiveState = true
      log.debug(s"Certificate proof generation is started.")
      sender() ! Success(Unit)
    }
  }

  protected def processStopProofGeneration: Receive = {
    case CertificateSubmitter.StopProofGeneration => {
      certGenerationActiveState = false
      log.debug(s"Certificate proof generation is finished.")
      sender() ! Success(Unit)
    }
  }

  protected def processGetProofGenerationState: Receive = {
    case CertificateSubmitterObserver.GetProofGenerationState => {
      sender() ! Success(certGenerationActiveState)
    }
  }
}

object CertificateSubmitterObserver {
  case object GetProofGenerationState
}

object CertificateSubmitterObserverRef {
  def props(): Props =
    Props(new CertificateSubmitterObserver())

  def apply()(implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props())

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams,
            mainchainApi: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(), name)
}
