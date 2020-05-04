package com.horizen.certificatesubmitter


import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen._
import com.horizen.block.SidechainBlock
import com.horizen.box.WithdrawalRequestBox
import com.horizen.mainchain.{CertificateRequest, CertificateRequestCreator}
import com.horizen.mainchain.api.RpcMainchainApi
import com.horizen.node.SidechainNodeView
import com.horizen.params.NetworkParams
import com.horizen.utils.WithdrawalEpochUtils
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import scorex.core.network.NodeViewSynchronizer.ReceivableMessages.SemanticallySuccessfulModifier
import scorex.util.ScorexLogging

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class CertificateSubmitter
  (settings: SidechainSettings,
   sidechainNodeViewHolderRef: ActorRef,
   params: NetworkParams)
  (implicit ec: ExecutionContext)
  extends Actor
  with ScorexLogging
{
  sealed trait SubmitResult

  case object SubmitSuccess
    extends SubmitResult {override def toString: String = "Backward transfer certificate was successfully created."}
  case class SubmitFailed(ex: Throwable)
    extends SubmitResult {override def toString: String = s"Backward transfer certificate creation was failed due to ${ex}"}

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  type SubmitMessageType = GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, SubmitResult]

  val timeoutDuration: FiniteDuration = settings.scorexSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[SemanticallySuccessfulModifier[SidechainBlock]])
  }

  lazy val mainchainApi = new RpcMainchainApi(settings)

  protected def submitCertificate(sidechainNodeView: View): SubmitResult = {
    try {

      val withdrawalEpochInfo = sidechainNodeView.state.getWithdrawalEpochInfo
      if (withdrawalEpochInfo.epoch > 0 &&
        WithdrawalEpochUtils.canSubmitCertificate(withdrawalEpochInfo, params)) {
        val withdrawalRequests = sidechainNodeView.state.unprocessedWithdrawalRequests(withdrawalEpochInfo.epoch - 1)
        if (withdrawalRequests.isDefined) {
          val mcEndEpochBlockHashOpt = sidechainNodeView.history
            .getMainchainBlockReferenceInfoByMainchainBlockHeight(
              params.mainchainCreationBlockHeight +
                withdrawalEpochInfo.epoch * params.withdrawalEpochLength - 1)
          mainchainApi.sendCertificate(
            CertificateRequestCreator.create(
              withdrawalEpochInfo.epoch - 1,
              mcEndEpochBlockHashOpt.get().getMainchainHeaderHash(),
              withdrawalRequests.get,
              params
            )
          )
        }
      }

      SubmitSuccess

    } catch {
      case e: Throwable => SubmitFailed(e)
    }
  }

  protected def trySubmitCertificate(): Unit = {
    val submitCertificateFunction: View => SubmitResult = submitCertificate

    val submitMessage: SubmitMessageType =
      GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, SubmitResult](submitCertificateFunction)

    val forgedBlockAsFuture = (sidechainNodeViewHolderRef ? submitMessage).asInstanceOf[Future[SubmitResult]]
    forgedBlockAsFuture.onComplete{
      case Success(SubmitSuccess) =>
        log.info(s"Backward transfer certificate was successfully created.")

      case Success(SubmitFailed(ex)) =>
        log.info("Creation of backward transfer certificate had been failed." + ex)

      case Failure(ex) =>
        log.info("Creation of backward transfer certificate had been failed. " + ex)
    }
  }

  override def receive: Receive = {
    case SemanticallySuccessfulModifier(sidechainBlock: SidechainBlock) => {
      trySubmitCertificate
    }
    case message: Any => log.error("CertificateSubmitter received strange message: " + message)
  }
}

object CertificateSubmitterRef {

  def props(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit ec: ExecutionContext) : Props =
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, params))

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, params), name)
}
