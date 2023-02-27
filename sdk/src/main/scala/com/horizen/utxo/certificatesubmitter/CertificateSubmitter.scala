package com.horizen.utxo.certificatesubmitter

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.api.http.client.SecureEnclaveApiClient
import com.horizen.block.SidechainBlockHeader
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter
import com.horizen.certificatesubmitter.dataproof.CertificateData
import com.horizen.certificatesubmitter.strategies._
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.cryptolibprovider.utils.CircuitTypes
import com.horizen.params.NetworkParams
import com.horizen.utxo.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import com.horizen.utxo.block.SidechainBlock
import com.horizen.utxo.chain.SidechainFeePaymentsInfo
import com.horizen.utxo.storage.SidechainHistoryStorage
import com.horizen.websocket.client.MainchainNodeChannel
import sparkz.core.NodeViewHolder.CurrentView

import scala.concurrent.ExecutionContext
import scala.language.postfixOps


class CertificateSubmitter[T <: CertificateData](settings: SidechainSettings,
                           sidechainNodeViewHolderRef: ActorRef,
                           secureEnclaveApiClient: SecureEnclaveApiClient,
                           params: NetworkParams,
                           mainchainChannel: MainchainNodeChannel,
                           submissionStrategy: CertificateSubmissionStrategy,
                           keyRotationStrategy: CircuitStrategy[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainHistory, SidechainState, T])
                          (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter[
    SidechainTypes#SCBT,
    SidechainBlockHeader,
    SidechainBlock,
    SidechainFeePaymentsInfo,
    SidechainHistoryStorage,
    SidechainHistory,
    SidechainState,
    SidechainWallet,
    SidechainMemoryPool,
    T
  ](settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy) {

  override type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
}


object CertificateSubmitterRef {
  def props(settings: SidechainSettings,
            sidechainNodeViewHolderRef: ActorRef,
            secureEnclaveApiClient: SecureEnclaveApiClient,
            params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props = {
    val submissionStrategy = if (params.isNonCeasing) {
      new NonCeasingSidechain(params)
    } else {
      new CeasingSidechain(mainchainChannel, params)
    }
    val keyRotationStrategy: CircuitStrategy[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainHistory, SidechainState, _ <: CertificateData] = if (params.circuitType.equals(CircuitTypes.NaiveThresholdSignatureCircuit)) {
      new WithoutKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.sigProofThresholdCircuitFunctions)
    } else {
      new WithKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation)
    }
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy))
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox")
  }

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel))
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref

  }

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel), name)
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref

  }
}
