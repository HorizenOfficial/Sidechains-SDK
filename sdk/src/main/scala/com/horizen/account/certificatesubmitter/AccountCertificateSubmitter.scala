package com.horizen.account.certificatesubmitter

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen._
import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.chain.AccountFeePaymentsInfo
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.storage.AccountHistoryStorage
import com.horizen.account.wallet.AccountWallet
import com.horizen.api.http.client.SecureEnclaveApiClient
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter
import com.horizen.certificatesubmitter.dataproof.CertificateData
import com.horizen.certificatesubmitter.strategies._
import com.horizen.certnative.BackwardTransfer
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.cryptolibprovider.utils.CircuitTypes
import com.horizen.mainchain.api.MainchainNodeCertificateApi
import com.horizen.params.NetworkParams
import com.horizen.websocket.client.MainchainNodeChannel

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.reflect.ClassTag


class AccountCertificateSubmitter[T <: CertificateData](settings: SidechainSettings,
                                                        sidechainNodeViewHolderRef: ActorRef,
                                                        secureEnclaveApiClient: SecureEnclaveApiClient,
                                                        params: NetworkParams,
                                                        mainchainChannel: MainchainNodeCertificateApi,
                                                        submissionStrategy: CertificateSubmissionStrategy,
                                                        keyRotationStrategy: CircuitStrategy[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, T])
                                                       (implicit ec: ExecutionContext)
  extends AbstractCertificateSubmitter[
    SidechainTypes#SCAT,
    AccountBlockHeader,
    AccountBlock,
    T
  ](settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy) {
  override type TX = SidechainTypes#SCAT
  override type H = AccountBlockHeader
  override type PM = AccountBlock

  override type FPI = AccountFeePaymentsInfo
  override type HSTOR = AccountHistoryStorage
  override type VL = AccountWallet
  override type HIS = AccountHistory
  override type MS = AccountState
  override type MP = AccountMemoryPool

  override implicit val tag: ClassTag[PM] = ClassTag[PM](classOf[PM])

}

object AccountCertificateSubmitterRef {
  def props(settings: SidechainSettings,
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
    val keyRotationStrategy: CircuitStrategy[SidechainTypes#SCAT, AccountBlockHeader, AccountBlock, _ <: CertificateData] = if (params.circuitType.equals(CircuitTypes.NaiveThresholdSignatureCircuit)) {
      new WithoutKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.sigProofThresholdCircuitFunctions)
    } else {
      new WithKeyRotationCircuitStrategy(settings, params, CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation)
    }
    Props(new AccountCertificateSubmitter(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel, submissionStrategy, keyRotationStrategy))
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox")
  }

  def apply(settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel)
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox"))

  def apply(name: String, settings: SidechainSettings, sidechainNodeViewHolderRef: ActorRef, secureEnclaveApiClient: SecureEnclaveApiClient, params: NetworkParams,
            mainchainChannel: MainchainNodeChannel)
           (implicit system: ActorSystem, ec: ExecutionContext): ActorRef =
    system.actorOf(props(settings, sidechainNodeViewHolderRef, secureEnclaveApiClient, params, mainchainChannel)
      .withMailbox("akka.actor.deployment.submitter-prio-mailbox"), name)
}
