package com.horizen

import java.net.InetSocketAddress
import java.lang.{Byte => JByte, Long => JLong}
import java.util.{HashMap => JHashMap, ArrayList => JArrayList}
import javafx.util.{Pair => JPair}

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import com.horizen.block.SidechainBlock
import com.horizen.box.{NoncedBox, RegularBox}
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.proposition.{Proposition, PublicKey25519Proposition}
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.transaction.{RegularTransaction, SidechainTransaction, TransactionSerializer}
import scorex.core.settings.{ScorexSettings, SettingsReaders}
import scorex.core.settings.ScorexSettings.readConfigFromPath
import scorex.util.ScorexLogging
import scorex.util._

case class WebSocketClientSettings(
                                    bindingAddress: InetSocketAddress = new InetSocketAddress("127.0.0.1", 8888),
                                    connectionTimeout : Long = 5000,
                                    connectionTimeUnit :String = "MILLISECONDS",
                                    responseTimeout : Long = 7000,
                                    responseTimeUnit :String = "MILLISECONDS")

case class SidechainSettings(scorexSettings: ScorexSettings, webSocketClientSettings: WebSocketClientSettings) {

  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())

  val secretKey = PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(123).getBytes)

  val targetSecretKey1 = PrivateKey25519Creator.getInstance().generateSecret("target1".getBytes)
  val targetSecretKey2 = PrivateKey25519Creator.getInstance().generateSecret("target2".getBytes)

  private def getGenesisTransactions: Seq[SidechainTransaction[Proposition, NoncedBox[Proposition]]] = {
    val fee = 0
    val timestamp = 1547798549470L

    val from = new JArrayList[JPair[RegularBox, PrivateKey25519]]
    val to = new JArrayList[JPair[PublicKey25519Proposition, JLong]]

    val creator = PrivateKey25519Creator.getInstance

    from.add(new JPair[RegularBox, PrivateKey25519](new RegularBox(secretKey.publicImage, 1, 30000L), secretKey))

    to.add(new JPair[PublicKey25519Proposition, JLong](targetSecretKey1.publicImage, 10000L))
    to.add(new JPair[PublicKey25519Proposition, JLong](targetSecretKey2.publicImage, 20000L))

    val transaction = RegularTransaction.create(from, to, fee, timestamp)
    Seq(transaction.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
  }

  lazy val genesisBlock : Option[SidechainBlock] = SidechainBlock.create(
    SidechainSettings.genesisParentBlockId,
    1565162709L, // Wednesday, August 7, 2019 7:25:09 AM
    Seq(),
    getGenesisTransactions,
    secretKey,
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