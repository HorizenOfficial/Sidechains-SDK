package com.horizen

import scorex.core.settings.{ScorexSettings, SettingsReaders}
import scorex.util.ScorexLogging
import java.io.File

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import scorex.core.bytesToId
import scorex.core.settings.ScorexSettings.readConfigFromPath
import scorex.core.settings._

case class SidechainSettings(scorexSettings: ScorexSettings);


object SidechainSettings extends ScorexLogging with SettingsReaders {
  def read(userConfigPath: Option[String]): SidechainSettings = {
    fromConfig(readConfigFromPath(userConfigPath, "scorex"))
  }

  implicit val networkSettingsValueReader: ValueReader[SidechainSettings] =
    (cfg: Config, path: String) => fromConfig(cfg.getConfig(path))

  private def fromConfig(config: Config): SidechainSettings = {
    log.info(config.toString)
//    val walletSettings = config.as[WalletSettings]("scorex.wallet")
//    val miningSettings = config.as[HybridMiningSettings]("scorex.miner")
    val scorexSettings = config.as[ScorexSettings]("scorex")
    SidechainSettings(scorexSettings)
  }
}
