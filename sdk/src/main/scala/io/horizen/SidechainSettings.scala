package io.horizen

import io.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import io.horizen.account.mempool.MempoolMap
import sparkz.core.settings.SparkzSettings

import java.math.BigInteger
import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class ForgerKeysData(
    blockSignProposition: String,
    vrfPublicKey: String,
)

case class WebSocketClientSettings(
    address: String,
    connectionTimeout: FiniteDuration,
    reconnectionDelay: FiniteDuration,
    reconnectionMaxAttempts: Int,
    // In Regtest allow to forge new blocks without connection to MC node, for example.
    allowNoConnectionInRegtest: Boolean = true,
    enabled: Boolean
)

case class WebSocketServerSettings(
                                    wsServer: Boolean = false,
                                    wsServerPort: Int = 8025
                                  )

case class GenesisDataSettings(scGenesisBlockHex: String,
                               scId: String,
                               mcBlockHeight: Int,
                               powData: String,
                               mcNetwork: String,
                               withdrawalEpochLength: Int,
                               initialCumulativeCommTreeHash: String,
                               isNonCeasing: Boolean
                              )

case class WithdrawalEpochCertificateSettings(
    submitterIsEnabled: Boolean,
    signersPublicKeys: Seq[String],
    signersThreshold: Int,
    signersSecrets: Seq[String],
    mastersPublicKeys: Seq[String] = Seq(),
    maxPks: Long,
    certProvingKeyFilePath: String,
    certVerificationKeyFilePath: String,
    circuitType: CircuitTypes = CircuitTypes.NaiveThresholdSignatureCircuit,
    certificateSigningIsEnabled: Boolean = true,
    certificateAutomaticFeeComputation: Boolean = true,
    certificateFee: String = "0.0001",
)

case class RemoteKeysManagerSettings(
    enabled: Boolean = false,
    address: String = "",
)

case class ForgerSettings(
    automaticForging: Boolean = false,
    restrictForgers: Boolean = false,
    allowedForgersList: Seq[ForgerKeysData] = Seq(),
)

case class MempoolSettings(
    maxSize: Int = 300,
    minFeeRate: Long = 0
)

case class WalletSettings(
    seed: String,
    genesisSecrets: Seq[String],
    maxTxFee: Long = 10000000,
)

case class CeasedSidechainWithdrawalSettings(
    cswProvingKeyFilePath: String,
    cswVerificationKeyFilePath: String,
)

case class LogInfoSettings(
    logFileName: String = "debug.log",
    logFileLevel: String = "all",
    logConsoleLevel: String = "error",
)

case class EthServiceSettings(
    /**
     * Global gas limit when executing messages via RPC calls this can be larger than the block gas limit, getter's
     * might require more gas than is ever required during a transaction.
     */
    globalRpcGasCap: BigInteger = BigInteger.valueOf(50000000),
)

// Default values are the same as in Geth/Erigon
case class AccountMempoolSettings(maxNonceGap: Int = 16,
                                  maxAccountSlots: Int = 16,
                                  maxMemPoolSlots: Int = 6144, // It is the sum of the default values of GlobalQueue and GlobalSlots in Geth
                                  maxNonExecMemPoolSlots: Int = 1024,
                                  txLifetime: FiniteDuration = 3.hours,
                                  allowUnprotectedTxs: Boolean = false){
  require(maxNonceGap > 0, s"Maximum Nonce Gap not positive: $maxNonceGap")
  require(maxAccountSlots > 0, s"Maximum Account Slots not positive: $maxAccountSlots")
  require(maxMemPoolSlots >= MempoolMap.MaxNumOfSlotsForTx, s"Maximum Memory Pool Slots number should be at least " +
    s"${MempoolMap.MaxNumOfSlotsForTx} but it is $maxMemPoolSlots")
  require(maxNonExecMemPoolSlots >= MempoolMap.MaxNumOfSlotsForTx, s"Maximum Non Executable Memory Sub Pool Slots number " +
    s"should be at least ${MempoolMap.MaxNumOfSlotsForTx} but it is $maxNonExecMemPoolSlots")
  require(maxNonExecMemPoolSlots < maxMemPoolSlots, s"Maximum Non Executable Memory Sub Pool Slots " +
    s"($maxNonExecMemPoolSlots) are greater than Maximum Memory Pool Slots ($maxMemPoolSlots)")
  require(maxMemPoolSlots >= maxAccountSlots, s"Maximum number of account slots cannot be bigger than maximum number of " +
    s"Memory Pool slots: account slots $maxAccountSlots - Memory Pool slots $maxMemPoolSlots")
  require(txLifetime.toSeconds > 0, s"Transaction lifetime cannot be 0 or less seconds: $txLifetime")

}

case class SidechainSettings(
    sparkzSettings: SparkzSettings,
    genesisData: GenesisDataSettings,
    websocketClient: WebSocketClientSettings,
    websocketServer: WebSocketServerSettings,
    withdrawalEpochCertificateSettings: WithdrawalEpochCertificateSettings,
    remoteKeysManagerSettings: RemoteKeysManagerSettings,
    mempool: MempoolSettings,
    wallet: WalletSettings,
    forger: ForgerSettings,
    csw: CeasedSidechainWithdrawalSettings,
    logInfo: LogInfoSettings,
    ethService: EthServiceSettings,
    accountMempool: AccountMempoolSettings,
)
