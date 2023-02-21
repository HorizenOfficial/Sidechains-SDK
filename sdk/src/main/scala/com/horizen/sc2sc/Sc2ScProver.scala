package com.horizen.sc2sc

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.pattern.ask
import akka.util.Timeout
import com.horizen.{AbstractHistory, AbstractState, SidechainAppEvents, SidechainHistory, SidechainSettings, SidechainState, SidechainTypes, Wallet}
import com.horizen.api.http.client.SecureEnclaveApiClient
import com.horizen.block.{SidechainBlock, SidechainBlockBase, SidechainBlockHeader, SidechainBlockHeaderBase}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.{CertificateSignatureFromRemoteInfo, SignaturesStatus}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.InternalReceivableMessages.LocallyGeneratedSignature
import com.horizen.certificatesubmitter.CertificateSubmitter
import com.horizen.certificatesubmitter.dataproof.CertificateData
import com.horizen.certificatesubmitter.strategies.{CeasingSidechain, CertificateSubmissionStrategy, CircuitStrategy, NonCeasingSidechain, SubmissionWindowStatus, WithKeyRotationCircuitStrategy, WithoutKeyRotationCircuitStrategy}
import com.horizen.chain.AbstractFeePaymentsInfo
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.cryptolibprovider.utils.CircuitTypes
import com.horizen.mainchain.api.MainchainNodeCertificateApi
import com.horizen.params.NetworkParams
import com.horizen.sc2sc.Sc2scProver.ReceivableMessages.BuildRedeemMessage
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction
import com.horizen.websocket.client.MainchainNodeChannel
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView
import sparkz.core.transaction.MemoryPool
import sparkz.util.SparkzLogging

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class Sc2ScProver [
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PM <: SidechainBlockBase[TX, H] : ClassTag,
  FPI <: AbstractFeePaymentsInfo,
  HSTOR <: AbstractHistoryStorage[PM, FPI, HSTOR],
  HIS <: AbstractHistory[TX, H, PM, FPI, HSTOR, HIS],
  MS <: AbstractState[TX, H, PM, MS],
  VL <: Wallet[SidechainTypes#SCS, SidechainTypes#SCP, TX, PM, VL],
  MP <: MemoryPool[TX, MP],
  T <: CertificateData](settings: SidechainSettings,
                        sidechainNodeViewHolderRef: ActorRef,
                        params: NetworkParams,
                        )
                       (implicit ec: ExecutionContext) extends Actor
  with Timers
  with SparkzLogging
  with Sc2ScUtils[TX, H, PM, MS, HIS]
{

  type View = CurrentView[HIS, MS, VL, MP]
  val timeoutDuration: FiniteDuration = settings.sparkzSettings.restApi.timeout
  implicit val timeout: Timeout = Timeout(timeoutDuration)

  override def receive: Receive = {
    case BuildRedeemMessage(message: CrossChainMessage) =>
      sender() ! buildRedeemMessage(message)
  }

  def buildRedeemMessage(message: CrossChainMessage): Try[CrossChainRedeemMessage] = {
    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView((view: View) =>
      buildRedeemMessage(message, view.state, view.history, params)),timeoutDuration)
      .asInstanceOf[Try[CrossChainRedeemMessage]]
  }
}

object Sc2ScProverRef {
  def props(settings: SidechainSettings,
            sidechainNodeViewHolderRef: ActorRef,
            params: NetworkParams
           )
           (implicit ec: ExecutionContext): Props = {
    Props(new Sc2ScProver(settings, sidechainNodeViewHolderRef, params))
  }

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings, sidechainNodeViewHolderRef, params))
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref
  }

  def apply(name: String, settings: SidechainSettings,sidechainNodeViewHolderRef: ActorRef, params: NetworkParams)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings,  sidechainNodeViewHolderRef,  params), name)
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref
  }
}


object Sc2scProver {
  // Public interface
  object ReceivableMessages {
    case class BuildRedeemMessage(crossChainMessage: CrossChainMessage)
  }
}
