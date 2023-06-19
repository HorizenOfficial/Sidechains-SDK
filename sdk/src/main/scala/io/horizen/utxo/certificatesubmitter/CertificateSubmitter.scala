package io.horizen.utxo.certificatesubmitter

import akka.actor.{ActorRef, ActorSystem, Props}
import io.horizen._
import io.horizen.api.http.client.SecureEnclaveApiClient
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter
import io.horizen.certificatesubmitter.dataproof.CertificateData
import io.horizen.certificatesubmitter.strategies._
import io.horizen.cryptolibprovider.{CircuitTypes, CryptoLibProvider}
import io.horizen.params.NetworkParams
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.chain.SidechainFeePaymentsInfo
import io.horizen.utxo.history.SidechainHistory
import io.horizen.utxo.mempool.SidechainMemoryPool
import io.horizen.utxo.state.SidechainState
import io.horizen.utxo.storage.SidechainHistoryStorage
import io.horizen.utxo.wallet.SidechainWallet
import io.horizen.websocket.client.MainchainNodeChannel
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.utils.TimeProvider

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
            timeProvider: TimeProvider,
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
      new WithKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation, timeProvider)
    }
    Props(new CertificateSubmitter(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy))
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox")
  }

  def apply(settings: SidechainSettings, timeProvider: TimeProvider, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings, timeProvider, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel))
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref

  }

  def apply(name: String, settings: SidechainSettings, timeProvider: TimeProvider, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef = {
    val ref = system.actorOf(props(settings, timeProvider, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel), name)
    system.eventStream.subscribe(ref, SidechainAppEvents.SidechainApplicationStart.getClass)
    ref

  }
}
