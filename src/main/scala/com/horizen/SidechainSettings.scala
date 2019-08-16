package com.horizen

import java.lang.{Byte => JByte}
import java.net.InetSocketAddress
import java.util.{HashMap => JHashMap}
import java.time.Instant

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import com.horizen.block.SidechainBlock
import com.horizen.box.NoncedBox
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.proposition.Proposition
import com.horizen.secret.PrivateKey25519Creator
import com.horizen.transaction.{SidechainTransaction, TransactionSerializer}
import scorex.core.settings.{ScorexSettings, SettingsReaders}
import scorex.core.settings.ScorexSettings.readConfigFromPath
import scorex.util.ScorexLogging
import scorex.util._

case class WebSocketClientSettings(
                                    bindingAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 8888),
                                    connectionTimeout : Long = 5000,
                                    connectionTimeUnit :String = "MILLISECONDS")

case class SidechainSettings(scorexSettings: ScorexSettings, webSocketClientSettings: WebSocketClientSettings) {

  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())

  private def getGenesisTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = {
    Seq()
  }

  lazy val genesisBlock : Option[SidechainBlock] = SidechainBlock.create(
    SidechainSettings.genesisParentBlockId,
    1565162709L, // Wednesday, August 7, 2019 7:25:09 AM
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

  val genesisParentBlockId : scorex.core.block.Block.BlockId = bytesToId(Random.randomBytes())

  def read(userConfigPath: Option[String]): SidechainSettings = {
    fromConfig(readConfigFromPath(userConfigPath, "scorex"))
    //new SidechainSettings(ScorexSettings.read(userConfigPath))
  }

  private def fromConfig(config: Config): SidechainSettings = {
    val webSocketClientSettings = config.as[WebSocketClientSettings]("scorex.websocket")
    val scorexSettings = config.as[ScorexSettings]("scorex")
    SidechainSettings(scorexSettings, webSocketClientSettings)
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