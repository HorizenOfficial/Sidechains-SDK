package com.horizen.certificatesubmitter

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
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
    context.system.eventStream.subscribe(self, CertificateSubmitter.StartCertificateSubmission.getClass)
    context.system.eventStream.subscribe(self, CertificateSubmitter.StopCertificateSubmission.getClass)
  }

  protected def processStartProofGeneration: Receive = {
    case CertificateSubmitter.StartCertificateSubmission => {
      certGenerationActiveState = true
      log.debug(s"Certificate proof generation is started.")
      Success(Unit)
    }
  }

  protected def processStopProofGeneration: Receive = {
    case CertificateSubmitter.StopCertificateSubmission => {
      certGenerationActiveState = false
      log.debug(s"Certificate proof generation is finished.")
      Success(Unit)
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

  def apply(name: String)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(), name)
}
