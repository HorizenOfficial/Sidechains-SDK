package com.horizen

import java.io.File
import java.net.URL
import java.util.{Optional => JOptional}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import sparkz.core.settings.{SparkzSettings, SettingsReaders}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.EnumerationReader._

import scala.compat.java8.OptionConverters.toScala


object SidechainSettingsReader
  extends LazyLogging
    with SettingsReaders
{
  protected val sidechainSettingsName = "sidechain-sdk-settings.conf"

  def fromConfig(config: Config): SidechainSettings = {
    val webSocketConnectorConfigurationSettings = config.as[WebSocketSettings]("sparkz.websocket")
    val sparkzSettings = config.as[SparkzSettings]("sparkz")
    val genesisSettings = config.as[GenesisDataSettings]("sparkz.genesis")
    val certificateSettings = config.as[WithdrawalEpochCertificateSettings]("sparkz.withdrawalEpochCertificate")
    val remoteKeysManagerSettings = config.as[RemoteKeysManagerSettings]("sparkz.remoteKeysManager")
    val mempoolSettings = config.as[MempoolSettings]("sparkz.mempool")
    val walletSettings = config.as[WalletSettings]("sparkz.wallet")
    val forgerSettings = config.as[ForgerSettings]("sparkz.forger")
    val cswSettings = config.as[CeasedSidechainWithdrawalSettings]("sparkz.csw")
    val logInfoSettings = config.as[LogInfoSettings]("sparkz.logInfo")

    SidechainSettings(sparkzSettings, genesisSettings, webSocketConnectorConfigurationSettings, certificateSettings,
      remoteKeysManagerSettings, mempoolSettings, walletSettings, forgerSettings, cswSettings, logInfoSettings)
  }

  def readConfigFromPath(userConfigPath: String, applicationConfigPath: Option[String]): Config = {

    val userConfigFile: File = new File(userConfigPath)

    val userConfig: Option[Config] = if (userConfigFile.exists()) {
      Some(ConfigFactory.parseFile(userConfigFile))
    } else None

    val applicationConfigURL: Option[URL] = applicationConfigPath.map(filename => new File(filename))
      .filter(_.exists()).map(_.toURI.toURL)
      .orElse(applicationConfigPath.map(r => getClass.getClassLoader.getResource(r)))

    val applicationConfig: Option[Config] = if (applicationConfigURL.isDefined) {
      Some(ConfigFactory.parseURL(applicationConfigURL.get))
    } else None

    var config: Config = ConfigFactory.defaultOverrides()

    if (userConfig.isDefined)
      config = config.withFallback(userConfig.get)

    if (applicationConfig.isDefined)
      config = config.withFallback(applicationConfig.get)

    config = config
      .withFallback(ConfigFactory.parseResources(sidechainSettingsName))
      .withFallback(ConfigFactory.defaultReference())
      .resolve()

    config
  }

  def readConfigFromPath(userConfigPath: String, applicationConfigPath: JOptional[String]) : Config =
    readConfigFromPath(userConfigPath, toScala(applicationConfigPath))

  def read(userConfigPath: String, applicationConfigPath: Option[String]) : SidechainSettings =
    fromConfig(readConfigFromPath(userConfigPath, applicationConfigPath))
}
