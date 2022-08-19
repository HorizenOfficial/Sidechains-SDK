package com.horizen

import scorex.core.settings.ScorexSettings

import scala.concurrent.duration.FiniteDuration

case class ForgerKeysData(blockSignProposition: String,
                          vrfPublicKey: String)

case class WebSocketClientSettings(address: String,
                             connectionTimeout: FiniteDuration,
                             reconnectionDelay: FiniteDuration,
                             reconnectionMaxAttempts: Int,
                             allowNoConnectionInRegtest: Boolean = true, // In Regtest allow to forge new blocks without connection to MC node, for example.
                             enabled: Boolean
                             )

case class WebSocketServerSettings(address: String,
                             wsServer: Boolean = false,
                             wsServerPort: Int = 8025
                            )

case class GenesisDataSettings(scGenesisBlockHex: String,
                               scId: String,
                               mcBlockHeight: Int,
                               powData: String,
                               mcNetwork: String,
                               withdrawalEpochLength: Int,
                               initialCumulativeCommTreeHash: String
                              )

case class WithdrawalEpochCertificateSettings(submitterIsEnabled: Boolean,
                                              signersPublicKeys: Seq[String],
                                              signersThreshold: Int,
                                              signersSecrets: Seq[String],
                                              maxPks: Long,
                                              certProvingKeyFilePath: String,
                                              certVerificationKeyFilePath: String,
                                              certificateSigningIsEnabled: Boolean = true,
                                              certificateAutomaticFeeComputation: Boolean = true,
                                              certificateFee: String = "0.0001"
                                             )

case class ForgerSettings(automaticForging: Boolean = false,
                          restrictForgers: Boolean = false,
                          allowedForgersList: Seq[ForgerKeysData] = Seq())

case class MempoolSettings(maxSize: Int = 300, minFeeRate: Long = 0)

case class WalletSettings(seed: String,
                          genesisSecrets: Seq[String],
                          maxTxFee: Long = 10000000)

case class CeasedSidechainWithdrawalSettings(cswProvingKeyFilePath: String,
                                             cswVerificationKeyFilePath: String)

case class LogInfoSettings(logFileName: String = "debug.log",
                           logFileLevel: String = "all",
                           logConsoleLevel: String = "error")

case class SidechainSettings(
                              scorexSettings: ScorexSettings,
                              genesisData: GenesisDataSettings,
                              websocketClient: WebSocketClientSettings,
                              websocketServer: WebSocketServerSettings,
                              withdrawalEpochCertificateSettings: WithdrawalEpochCertificateSettings,
                              mempool: MempoolSettings,
                              wallet: WalletSettings,
                              forger: ForgerSettings,
                              csw: CeasedSidechainWithdrawalSettings,
                              logInfo: LogInfoSettings
                            )
