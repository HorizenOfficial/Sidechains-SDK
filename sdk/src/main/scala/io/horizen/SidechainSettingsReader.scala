package io.horizen

import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import net.ceedubs.ficus.readers.EnumerationReader._
import sparkz.core.settings.{RESTApiSettings, SettingsReaders, SparkzSettings}

import java.io.File
import java.math.BigInteger
import java.net.URL
import java.util.{Optional => JOptional}
import scala.compat.java8.OptionConverters.toScala


object SidechainSettingsReader
  extends LazyLogging
    with SettingsReaders
{
  protected val sidechainSettingsName = "sidechain-sdk-settings.conf"

  // allows config values to be parsed into BigInteger
  private implicit val bigIntegerReader: ValueReader[BigInteger] = new ValueReader[BigInteger] { config =>
    def read(config: Config, path: String): BigInteger = {
      val s = config.getString(path)
      try {
        new BigInteger(s, 10)
      } catch {
        case e: NumberFormatException => throw new ConfigException.WrongType(config.origin(), path, "java.math.BigInteger", "String", e)
      }
    }
  }

  def fromConfig(config: Config): SidechainSettings = {
    val webSocketClientSettings = config.as[WebSocketClientSettings]("sparkz.websocketClient")
    val webSocketServerSettings = config.as[WebSocketServerSettings]("sparkz.websocketServer")
    val sparkzSettings = config.as[SparkzSettings]("sparkz")
    val metricsSettings = config.as[MetricsApiSettings]("sparkz.metricsApi")
    val genesisSettings = config.as[GenesisDataSettings]("sparkz.genesis")
    val certificateSettings = config.as[WithdrawalEpochCertificateSettings]("sparkz.withdrawalEpochCertificate")
    val remoteKeysManagerSettings = config.as[RemoteKeysManagerSettings]("sparkz.remoteKeysManager")
    val mempoolSettings = config.as[MempoolSettings]("sparkz.mempool")
    val accountMempoolSettings = config.as[AccountMempoolSettings]("sparkz.accountMempool")
    val walletSettings = config.as[WalletSettings]("sparkz.wallet")
    val forgerSettings = config.as[ForgerSettings]("sparkz.forger")
    val cswSettings = config.as[CeasedSidechainWithdrawalSettings]("sparkz.csw")
    val logInfoSettings = config.as[LogInfoSettings]("sparkz.logInfo")
    val ethServiceSettings = config.as[EthServiceSettings]("sparkz.ethService")
    val apiRateLimiterSettings = config.as[ApiRateLimiterSettings]("sparkz.apiRateLimiter")
    val historySettings = config.as[HistorySettings]("sparkz.history")

    SidechainSettings(sparkzSettings, metricsSettings, genesisSettings, webSocketClientSettings, webSocketServerSettings, certificateSettings,
      remoteKeysManagerSettings, mempoolSettings, walletSettings, forgerSettings, cswSettings, logInfoSettings,
      ethServiceSettings, accountMempoolSettings, apiRateLimiterSettings, historySettings)
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
