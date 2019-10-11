package com.horizen

import java.lang.{Byte => JByte, Long => JLong}
import java.net.InetSocketAddress
import java.util.{ArrayList => JArrayList, HashMap => JHashMap}

import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.{NoncedBox, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.{RegularTransaction, SidechainTransaction, TransactionSerializer}
import com.horizen.utils.BytesUtils
import com.typesafe.config.Config
import javafx.util.{Pair => JPair}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import scorex.core.settings.ScorexSettings.readConfigFromPath
import scorex.core.settings.{ScorexSettings, SettingsReaders}
import scorex.util.{ScorexLogging, _}

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

object SidechainSettings
  extends ScorexLogging
    with SettingsReaders
{

  // TODO: Remove it from here
  val genesisParentBlockId : scorex.core.block.Block.BlockId = bytesToId(new Array[Byte](32))

  def read(userConfigPath: Option[String]): SidechainSettings = {
    fromConfig(readConfigFromPath(userConfigPath, "scorex"))
  }

  private def fromConfig(config: Config): SidechainSettings = {
    val webSocketClientSettings = WebSocketClientSettings()
    val scorexSettings = config.as[ScorexSettings]("scorex")
    val genesisSetting = config.as[GenesisData]("scorex.genesis")
    SidechainSettings(scorexSettings, genesisSetting, webSocketClientSettings)
  }
}