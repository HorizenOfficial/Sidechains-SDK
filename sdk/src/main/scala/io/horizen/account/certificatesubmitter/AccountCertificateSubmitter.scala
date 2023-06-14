package io.horizen.account.certificatesubmitter

import akka.actor.{ActorRef, ActorSystem, Props}
import io.horizen._
import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.chain.AccountFeePaymentsInfo
import io.horizen.account.history.AccountHistory
import io.horizen.account.mempool.AccountMemoryPool
import io.horizen.account.state.AccountState
import io.horizen.account.storage.AccountHistoryStorage
import io.horizen.account.wallet.AccountWallet
import io.horizen.api.http.client.SecureEnclaveApiClient
import io.horizen.certificatesubmitter.AbstractCertificateSubmitter
import io.horizen.certificatesubmitter.dataproof.CertificateData
import io.horizen.certificatesubmitter.strategies._
import io.horizen.cryptolibprovider.{CircuitTypes, CryptoLibProvider}
import io.horizen.mainchain.api.MainchainNodeCertificateApi
import io.horizen.params.NetworkParams
import io.horizen.websocket.client.MainchainNodeChannel
import sparkz.core.utils.TimeProvider

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class AccountCertificateSubmitter[T <: CertificateData](settings: SidechainSettings,
                                                        sidechainNodeViewHolderRef: ActorRef,
                                                        secureEnclaveApiClient: SecureEnclaveApiClient,
                                                        params: NetworkParams,
                                                        mainchainChannel: MainchainNodeCertificateApi,
                                                        submissionStrategy: CertificateSubmissionStrategy,
                                                        keyRotationStrategy: CircuitStrategy[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountHistory, AccountState, T])
                                                       (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    AccountFeePaymentsInfo,
    AccountHistoryStorage,
    AccountHistory,
    AccountState,
    AccountWallet,
    AccountMemoryPool,
    T
  ](settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy) {}

object AccountCertificateSubmitterRef {
  def props(settings: SidechainSettings,
            timeProvider: TimeProvider,
            sidechainNodeViewHolderRef: ActorRef,
            secureEnclaveApiClient: SecureEnclaveApiClient,
            params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit ec: ExecutionContext): Props = {
    val submissionStrategy: CertificateSubmissionStrategy = if (params.isNonCeasing) {
      new NonCeasingSidechain(params)
    } else {
      new CeasingSidechain(mainchainChannel, params)
    }
    val keyRotationStrategy: CircuitStrategy[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, AccountHistory, AccountState, _ <: CertificateData] = if (params.circuitType.equals(CircuitTypes.NaiveThresholdSignatureCircuit)) {
      new WithoutKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.sigProofThresholdCircuitFunctions)
    } else {
      new WithKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation, timeProvider)
    }
    Props(new AccountCertificateSubmitter(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy))
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
