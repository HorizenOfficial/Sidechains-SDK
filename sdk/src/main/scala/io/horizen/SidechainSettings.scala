package io.horizen

import io.horizen.account.mempool.MempoolMap
import io.horizen.cryptolibprovider.CircuitTypes
import io.horizen.cryptolibprovider.CircuitTypes.CircuitTypes
import sparkz.core.settings.SparkzSettings

import java.math.BigInteger
import scala.annotation.meta.field
import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class ForgerKeysData(
    blockSignProposition: String,
    vrfPublicKey: String,
) extends SensitiveStringer

case class WebSocketClientSettings(
    address: String,
    connectionTimeout: FiniteDuration,
    reconnectionDelay: FiniteDuration,
    reconnectionMaxAttempts: Int,
    // In Regtest allow to forge new blocks without connection to MC node, for example.
    allowNoConnectionInRegtest: Boolean = true,
    enabled: Boolean
) extends SensitiveStringer

case class WebSocketServerSettings(
    wsServer: Boolean = false,
    wsServerPort: Int = 8025,
    wsServerAllowedOrigins: Seq[String] = Seq()
)

case class GenesisDataSettings(
    scGenesisBlockHex: String,
    scId: String,
    mcBlockHeight: Int,
    powData: String,
    mcNetwork: String,
    withdrawalEpochLength: Int,
    initialCumulativeCommTreeHash: String,
    isNonCeasing: Boolean
) extends SensitiveStringer

case class WithdrawalEpochCertificateSettings(
    submitterIsEnabled: Boolean,
    signersPublicKeys: Seq[String],
    signersThreshold: Int,
    @(SensitiveString @field) signersSecrets: Seq[String],
    mastersPublicKeys: Seq[String] = Seq(),
    maxPks: Long,
    certProvingKeyFilePath: String,
    certVerificationKeyFilePath: String,
    circuitType: CircuitTypes = CircuitTypes.NaiveThresholdSignatureCircuit,
    certificateSigningIsEnabled: Boolean = true,
    certificateAutomaticFeeComputation: Boolean = true,
    certificateFee: String = "0.0001",
) extends SensitiveStringer

case class RemoteKeysManagerSettings(
    enabled: Boolean = false,
    address: String = "",
) extends SensitiveStringer

case class ForgerSettings(
    automaticForging: Boolean = false,
    restrictForgers: Boolean = false,
    allowedForgersList: Seq[ForgerKeysData] = Seq(),
) extends SensitiveStringer

case class MempoolSettings(
    maxSize: Int = 300,
    minFeeRate: Long = 0
) extends SensitiveStringer

case class WalletSettings(
    seed: String,
    @(SensitiveString @field) genesisSecrets: Seq[String],
    maxTxFee: Long = 10000000,
) extends SensitiveStringer

case class CeasedSidechainWithdrawalSettings(
    cswProvingKeyFilePath: String,
    cswVerificationKeyFilePath: String,
) extends SensitiveStringer

case class LogInfoSettings(
    logFileName: String = "debug.log",
    logFileLevel: String = "all",
    logConsoleLevel: String = "error",
) extends SensitiveStringer

case class EthServiceSettings(
    /**
     * Global gas limit when executing messages via RPC calls this can be larger than the block gas limit, getter's
     * might require more gas than is ever required during a transaction.
     */
    globalRpcGasCap: BigInteger = BigInteger.valueOf(50000000),
) extends SensitiveStringer

// Default values are the same as in Geth/Erigon
case class AccountMempoolSettings(
    maxNonceGap: Int = 16,
    maxAccountSlots: Int = 16,
    maxMemPoolSlots: Int = 6144, // It is the sum of the default values of GlobalQueue and GlobalSlots in Geth
    maxNonExecMemPoolSlots: Int = 1024,
    txLifetime: FiniteDuration = 3.hours,
    allowUnprotectedTxs: Boolean = false
) extends SensitiveStringer {
  require(maxNonceGap > 0, s"Maximum Nonce Gap not positive: $maxNonceGap")
  require(maxAccountSlots > 0, s"Maximum Account Slots not positive: $maxAccountSlots")
  require(
    maxMemPoolSlots >= MempoolMap.MaxNumOfSlotsForTx,
    s"Maximum Memory Pool Slots number should be at least ${MempoolMap.MaxNumOfSlotsForTx} but it is $maxMemPoolSlots"
  )
  require(
    maxNonExecMemPoolSlots >= MempoolMap.MaxNumOfSlotsForTx,
    s"Maximum Non Executable Memory Sub Pool Slots number should be at least ${MempoolMap.MaxNumOfSlotsForTx} but it is $maxNonExecMemPoolSlots"
  )
  require(
    maxNonExecMemPoolSlots < maxMemPoolSlots,
    s"Maximum Non Executable Memory Sub Pool Slots ($maxNonExecMemPoolSlots) are greater than Maximum Memory Pool Slots ($maxMemPoolSlots)"
  )
  require(
    maxMemPoolSlots >= maxAccountSlots,
    s"Maximum number of account slots cannot be bigger than maximum number of Memory Pool slots: account slots $maxAccountSlots - Memory Pool slots $maxMemPoolSlots"
  )
  require(txLifetime.toSeconds > 0, s"Transaction lifetime cannot be 0 or less seconds: $txLifetime")
}

case class Sc2ScSettings(
  sc2ScProvingKeyFilePath: Option[String],
  sc2ScVerificationKeyFilePath: Option[String]
)

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
    sc2sc: Sc2ScSettings
)
