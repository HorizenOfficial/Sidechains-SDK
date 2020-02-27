package com.horizen.certifier

import java.time.Instant

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen._
import com.horizen.box.WithdrawalRequestBox
import com.horizen.mainchain.{CertificateRequest, CertificateRequestCreator}
import com.horizen.mainchain.api.RpcMainchainApi
import com.horizen.params.NetworkParams
import scorex.core.NodeViewHolder.CurrentView
import scorex.util.ScorexLogging

import scala.concurrent.duration.FiniteDuration

class Certifier
  (settings: SidechainSettings,
   params: NetworkParams)
  extends Actor
  with ScorexLogging
{

  import Certifier.ReceivableMessages._

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  lazy val mainchainApi = new RpcMainchainApi(settings)

  protected def trySubmitCertificate: Receive = {
    case message: TrySubmitCertificate =>
      val certificateId = mainchainApi
        .sendCertificate(CertificateRequestCreator
          .create(message.epochNumber, message.endEpochBlockHash, message.withdrawalRequests, params))
      sender() ! Success(certificateId)
  }

  override def receive: Receive = {
    trySubmitCertificate orElse {
      case message: Any => log.error("Submitter received strange message: " + message)
    }
  }
}

object Certifier
  extends ScorexLogging
{
  import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

  object ReceivableMessages {

    case class TrySubmitCertificate(epochNumber: Int, endEpochBlockHash: Array[Byte], withdrawalRequests: Seq[WithdrawalRequestBox])
  }

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  val getCurrentNodeView: GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, View] = {
    def f(v: View): View = v
    GetDataFromCurrentView[SidechainHistory,
      SidechainState,
      SidechainWallet,
      SidechainMemoryPool,
      Certifier.View](f)
  }
}

object CertifierRef {
  def props(settings: SidechainSettings, params: NetworkParams): Props =
    Props(new Certifier(settings, params))

  def apply(settings: SidechainSettings, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, params))

  def apply(name: String, settings: SidechainSettings, params: NetworkParams)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(settings, params), name)
}
