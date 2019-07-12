package com.horizen

import scorex.core.settings.{ScorexSettings, SettingsReaders}
import scorex.util.ScorexLogging

case class SidechainSettings(scorexSettings: ScorexSettings);


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
