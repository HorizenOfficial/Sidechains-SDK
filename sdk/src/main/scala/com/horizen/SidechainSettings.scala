package com.horizen

import scorex.core.settings.ScorexSettings

import scala.concurrent.duration.FiniteDuration


case class WebSocketSettings(address: String,
                             connectionTimeout: FiniteDuration,
                             reconnectionDelay: FiniteDuration,
                             reconnectionMaxAttempts: Int
                            )

case class GenesisDataSettings(scGenesisBlockHex: String,
                               scId: String,
                               mcBlockHeight: Int,
                               powData: String,
                               mcNetwork: String,
                               withdrawalEpochLength: Int
                              )

case class BackwardTransfer(backwardTransferPublicKeys: Seq[String],
                            backwardTransferThreshold: Int,
                            backwardTransferSecrets: Seq[String],
                            poseidonRootHash: String
                           )

case class WalletSettings(seed: String,
                          genesisSecrets: Seq[String]
                         )


case class SidechainSettings(scorexSettings: ScorexSettings,
                             genesisData: GenesisDataSettings,
                             websocket: WebSocketSettings,
                             backwardTransfer: BackwardTransfer,
                             wallet: WalletSettings
                            )