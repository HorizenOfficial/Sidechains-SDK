package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import java.time.Instant

import com.horizen.block.SidechainBlock
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.secret.PrivateKey25519Creator
import com.horizen.transaction.TransactionSerializer
import scorex.core.settings.{ScorexSettings, SettingsReaders}
import scorex.util.ScorexLogging
import scorex.util._

case class SidechainSettings(scorexSettings: ScorexSettings) {

  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())

  lazy val genesisBlock : Option[SidechainBlock] = SidechainBlock.create(
    bytesToId(new Array[Byte](32)),
    Instant.now.getEpochSecond - 10000,
    Seq(),
    Seq(),
    PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(123).getBytes),
    sidechainTransactionsCompanion,
    null
  ).toOption

}


object SidechainSettings
  extends ScorexLogging
    with SettingsReaders
{
  def read(userConfigPath: Option[String]): SidechainSettings = {
    //fromConfig(readConfigFromPath(userConfigPath, "scorex"))
    new SidechainSettings(ScorexSettings.read(userConfigPath))
  }

  /*
  implicit val networkSettingsValueReader: ValueReader[SDKSettings] =
    (cfg: Config, path: String) => fromConfig(cfg.getConfig(path))
  */

  /*
  private def fromConfig(config: Config): HybridSettings = {
    log.info(config.toString)
    val walletSettings = config.as[WalletSettings]("scorex.wallet")
    val miningSettings = config.as[HybridMiningSettings]("scorex.miner")
    val scorexSettings = config.as[ScorexSettings]("scorex")
    HybridSettings(miningSettings, walletSettings, scorexSettings)
  }*/
}