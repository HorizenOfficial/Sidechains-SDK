package com.horizen

import scorex.core.settings.ScorexSettings

import scala.concurrent.duration.FiniteDuration

case class ForgerKeysData(blockSignProposition: String,
                          vrfPublicKey: String)

case class WebSocketSettings(address: String,
                             connectionTimeout: FiniteDuration,
                             reconnectionDelay: FiniteDuration,
                             reconnectionMaxAttempts: Int,
                             allowNoConnectionInRegtest: Boolean = true, // In Regtest allow to forge new blocks without connection to MC node, for example.
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

case class WalletSettings(seed: String,
                          genesisSecrets: Seq[String])

case class CeasedSidechainWithdrawalSettings(cswProvingKeyFilePath: String,
                                             cswVerificationKeyFilePath: String)

case class LogInfoSettings(logFileName: String = "debug.log",
                           logFileLevel: String = "all",
                           logConsoleLevel: String = "error")

case class SidechainSettings(
                              scorexSettings: ScorexSettings,
                              genesisData: GenesisDataSettings,
                              websocket: WebSocketSettings,
                              withdrawalEpochCertificateSettings: WithdrawalEpochCertificateSettings,
                              wallet: WalletSettings,
                              forger: ForgerSettings,
                              csw: CeasedSidechainWithdrawalSettings,
                              logInfo: LogInfoSettings
                            )
