package io.horizen

import com.horizen.cryptolibprovider.CircuitTypes
import CircuitTypes.CircuitTypes
import sparkz.core.settings.SparkzSettings

import java.math.BigInteger
import scala.concurrent.duration.FiniteDuration

case class ForgerKeysData(
    blockSignProposition: String,
    vrfPublicKey: String,
)

case class WebSocketSettings(
    address: String,
    connectionTimeout: FiniteDuration,
    reconnectionDelay: FiniteDuration,
    reconnectionMaxAttempts: Int,
    // In Regtest allow to forge new blocks without connection to MC node, for example.
    allowNoConnectionInRegtest: Boolean = true,
    wsServer: Boolean = false,
    wsServerPort: Int = 8025,
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
    minFeeRate: Long = 0,
    allowUnprotectedTxs: Boolean = false
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

case class SidechainSettings(
    sparkzSettings: SparkzSettings,
    genesisData: GenesisDataSettings,
    websocket: WebSocketSettings,
    withdrawalEpochCertificateSettings: WithdrawalEpochCertificateSettings,
    remoteKeysManagerSettings: RemoteKeysManagerSettings,
    mempool: MempoolSettings,
    wallet: WalletSettings,
    forger: ForgerSettings,
    csw: CeasedSidechainWithdrawalSettings,
    logInfo: LogInfoSettings,
    ethService: EthServiceSettings,
)
