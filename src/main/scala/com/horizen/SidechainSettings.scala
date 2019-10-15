package com.horizen

import java.io.File
import java.lang.{Byte => JByte, Long => JLong}
import java.net.{InetSocketAddress, URL}
import java.util.{ArrayList => JArrayList, HashMap => JHashMap}
import java.util.{Optional => JOptional}
import javafx.util.{Pair => JPair}

import com.typesafe.config.{Config, ConfigFactory}
import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.{NoncedBox, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.storage.{IODBStoreAdapter, Storage}
import com.horizen.transaction.{RegularTransaction, SidechainTransaction, TransactionSerializer}
import com.horizen.utils.BytesUtils
import com.typesafe.config.Config
import io.iohk.iodb.LSMStore
import javafx.util.{Pair => JPair}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import scorex.core.settings.{ScorexSettings, SettingsReaders}
import scorex.util.{ScorexLogging, _}
import scala.compat.java8.OptionConverters._

case class WebSocketClientSettings(
                                    remoteAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 8888),
                                    connectionTimeout : Long = 5000,
                                    connectionTimeUnit :String = "MILLISECONDS",
                                    responseTimeout : Long = 7000,
                                    responseTimeUnit :String = "MILLISECONDS")

case class GenesisData(
                        scGenesisBlockHex: String,
                        scId: String,
                        mcBlockHeight: Int,
                        powData: String,
                        mcNetwork: String
                      )
case class SidechainSettings(scorexSettings: ScorexSettings, genesisData: GenesisData, webSocketClientSettings: WebSocketClientSettings) {

  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())

  // TO DO: remove key related data. Basic secrets list should be a part of config file.
  val secretKey = PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(123).getBytes)

  val targetSecretKey1 = PrivateKey25519Creator.getInstance().generateSecret("target1".getBytes)
  val targetSecretKey2 = PrivateKey25519Creator.getInstance().generateSecret("target2".getBytes)

  val pub1hex = BytesUtils.toHexString(targetSecretKey1.publicImage().bytes())
  val pub2hex = BytesUtils.toHexString(targetSecretKey2.publicImage().bytes())

  // TO DO: remove this data from here
  lazy val genesisBlock: Option[SidechainBlock] = Some(
    new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      BytesUtils.fromHexString(genesisData.scGenesisBlockHex)
    )
  )

  // TO DO: remove this data from here
  val genesisPowData: Seq[(Int, Int)] = {
    var res: Seq[(Int, Int)] = Seq()
    val powDataBytes: Array[Byte] = BytesUtils.fromHexString(genesisData.powData)
    var offset = 0
    while(offset < powDataBytes.length) {
      res = res :+ (
        BytesUtils.getReversedInt(powDataBytes, offset),
        BytesUtils.getReversedInt(powDataBytes, offset + 4)
      )
      offset += 8
    }
    res
  }
}


object SidechainSettingsReader
  extends ScorexLogging
    with SettingsReaders
{

  protected val sidechainSettingsName = "sidechain-sdk-settings.conf"
  val genesisParentBlockId : scorex.core.block.Block.BlockId = bytesToId(new Array[Byte](32))

  def fromConfig(config: Config): SidechainSettings = {
    val webSocketClientSettings = config.as[WebSocketClientSettings]("scorex.websocket")
    val scorexSettings = config.as[ScorexSettings]("scorex")
    val genesisSetting = config.as[GenesisData]("scorex.genesis")
    SidechainSettings(scorexSettings, genesisSetting, webSocketClientSettings)
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