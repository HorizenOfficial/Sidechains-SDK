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

case class SidechainSettings(scorexSettings: ScorexSettings, webSocketClientSettings: WebSocketClientSettings) {

  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())

  val secretKey = PrivateKey25519Creator.getInstance().generateSecret("genesis_seed%d".format(123).getBytes)

  val targetSecretKey1 = PrivateKey25519Creator.getInstance().generateSecret("target1".getBytes)
  val targetSecretKey2 = PrivateKey25519Creator.getInstance().generateSecret("target2".getBytes)

  val pub1hex = BytesUtils.toHexString(targetSecretKey1.publicImage().bytes())
  val pub2hex = BytesUtils.toHexString(targetSecretKey2.publicImage().bytes())

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
    val id = transaction.id
    Seq(transaction.asInstanceOf[SidechainTransaction[Proposition, NoncedBox[Proposition]]])
  }

  lazy val genesisBlock : Option[SidechainBlock] = {
    /*// hash is "0a8e90055411a87e39da913a0d7b3c815a19a63c51b7f0138987968f57ec40a4"
    val mcblockhex = "00000020a440ec578f96878913f0b7513ca6195a813c7b0d3a91da397ea8115405908e0a2598c3fc0d8a572a6b567b0f30a01d4e076545d566cb4bd3aa6cee0ef47c98249f6f8f26c370780ff038b29330b0263948f29031135af9971bc7f6ddf5add437041a965d020f0f200f007a620b4e4b033254423945c5ba295faac855c7c9cf1a0fa88f10161000002400ae058a41bb347fba5de12355ba8f8fb7ec2779133c941c5a7d622924de9fd68e8201350201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502e4000101ffffffff04921cb42c000000001976a914422647aa673f5b4116852e64705ef571bb822b9488ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff07020708b1ee908d04a9d09d0b1eed3cdb80e31d351d8fce3f093fe567fe35ae4c000000006b4830450221009b67b4cb689ac56be9b6d8b2be5740379aefb59b1cefb73373689ed0e2647bd3022026574acea45bdaf365b3d9597e109a0c1c9dc59064619c0aaae2f0437786887c01210344147fea2c6fe4a9712b1a384ebf904f2ca83aace643f8424ff81137baf94b3bfeffffff067901983e15619c39d3db470f0fef008ee86c33809e6eccba46ff18093aad03000000006b483045022100ab9aae819b5bca5a3995d651db2735a273a7339219feb59b0c2d23c9ea08067302207925878f2502230a5ec14d5e5777aaf78c9bc8a5557d2a3f889d1074af8a906501210344147fea2c6fe4a9712b1a384ebf904f2ca83aace643f8424ff81137baf94b3bfeffffff198857b2555f9ef9146aa169681057ae18f7a036691832996d48cdf040843fa2000000006b4830450221008f84563b3ac408557ee1c8047b5457eb89c4a0f440eb02f335f1f43fbd3eb68d02205eece8f685734fef4e7f942a7711a5c3d5566632d5b53f8c98722b88e470cded01210344147fea2c6fe4a9712b1a384ebf904f2ca83aace643f8424ff81137baf94b3bfeffffff3213e4d571aa03df7778818829b0d4fdaa517b55082cc261204786c2e532d4c9000000006b483045022100c03d8bf5ab09adfeb5b23d67af781f2d4f254d1a5805d4eb572395b77f178f960220113a19c65fe6800fe3b70a4ae99d3ebdbab810001ea9f5069a9220b73d4e60e301210344147fea2c6fe4a9712b1a384ebf904f2ca83aace643f8424ff81137baf94b3bfeffffff858110d556dc2b63f2f85a39a811b8ed37275720e1c6809e8f60486719686839000000006b483045022100ac585a8ef5e5125855b6962b4b9fbce78306485e12a353e3b10f6c65bd83eca802203a14012669f5af4487353238d45ed2fc63ee6abff9fb4a717193602c3eaf4ee201210344147fea2c6fe4a9712b1a384ebf904f2ca83aace643f8424ff81137baf94b3bfefffffff48e55f431b4f74115c2e4d76cfaec51d525645b9ba8cff5f006565dc1ffb831000000006a473044022045c33799a981bc05bc5b2fb26a388e936743b4fa0c8964e6b6f7f53d558f7b7802202aeed30734c439a9e0f144f706ba3f298b0f577d09703f8f0e812966f1f0b71901210344147fea2c6fe4a9712b1a384ebf904f2ca83aace643f8424ff81137baf94b3bfefffffff7b07a9191fe532f997793e5499e5491231854927971635c39b981ab7ca6f893000000006a4730440220362bce18d9240e45acf4d787904665ae95feb491bd3d9f1eba3a605dbaf3237702203e03a4a8394ef50aec3690a31825a5647e515633a96a9462605f3f899365f3c801210344147fea2c6fe4a9712b1a384ebf904f2ca83aace643f8424ff81137baf94b3bfeffffff01fe585f00000000003c76a9141bac95724cec6ebd180d73f2d2a662835245019088ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010100000000000000000000000000000000000000000000000000000000000000e80300000002005ed0b20000000040eec4573bf5ceeb468a5c67808c2218927c9c4411ecd6b7f106b81d8d6e12bf010000000000000000000000000000000000000000000000000000000000000000f2052a01000000163076d7df8356a0d2322d8883651d1fe585bfb59c52104e87bcac5a03af711d0100000000000000000000000000000000000000000000000000000000000000d9000000"

    case class CustomParams(override val sidechainId: Array[Byte]) extends RegTestParams {

    }

    val params = CustomParams(BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001"))
    val gbhex = BytesUtils.toHexString(SidechainBlock.create(
      SidechainSettings.genesisParentBlockId,
      1565162709L, // Wednesday, August 7, 2019 7:25:09 AM
      Seq(MainchainBlockReference.create(BytesUtils.fromHexString(mcblockhex), params).get),
      Seq(), //getGenesisTransactions,
      secretKey,
      sidechainTransactionsCompanion,
      params
    ).get.bytes)*/
    Some(
    new SidechainBlockSerializer(sidechainTransactionsCompanion).parseBytes(
      // Hex representation of Sidehcain block generated by commented code below and above.
      BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000aaf3d3d40ba009029a09e20200000020a440ec578f96878913f0b7513ca6195a813c7b0d3a91da397ea8115405908e0a2598c3fc0d8a572a6b567b0f30a01d4e076545d566cb4bd3aa6cee0ef47c98249f6f8f26c370780ff038b29330b0263948f29031135af9971bc7f6ddf5add437041a965d020f0f200f007a620b4e4b033254423945c5ba295faac855c7c9cf1a0fa88f10161000002400ae058a41bb347fba5de12355ba8f8fb7ec2779133c941c5a7d622924de9fd68e820135ac0537d4adf5ddf6c71b97f95a133190f2483926b03093b238f00f7870c3268f6f9f000000005d961a040000012a069201da01da01030100000000000000000000000000000000000000000000000000000000000000e8030000092434443090220e8c58a7bd1b35a912b32854ab0aa9771c652aed9bcb1dd5640000000001005ed0b20000000040eec4573bf5ceeb468a5c67808c2218927c9c4411ecd6b7f106b81d8d6e12bf0100000000000000000000000000000000000000000000000000000000000000092434443090220e8c58a7bd1b35a912b32854ab0aa9771c652aed9bcb1dd564000000010100f2052a01000000163076d7df8356a0d2322d8883651d1fe585bfb59c52104e87bcac5a03af711d0100000000000000000000000000000000000000000000000000000000000000092434443090220e8c58a7bd1b35a912b32854ab0aa9771c652aed9bcb1dd564000000028001000000000000000000000000000000000000000000000000000000000000000137d4adf5ddf6c71b97f95a133190f2483926b03093b238f00f7870c3268f6f9f02003cfbf879f515ff96c9031ebcbc006170dd6c30522a35d488c7472c4a75dc1148c32a25ef67bb4df79fe2e743b6d0bda9044f37bbb9dfa9e95fb4a75a64e92e89228e792ae237bf25f9a82dc5de58a0b3c7dedfb85a20fd1085b3bf7792341f0d")
    )
  )
}

  /*Some(new SidechainBlock(
    SidechainSettings.genesisParentBlockId,
    1565162709L, // Wednesday, August 7, 2019 7:25:09 AM
    Seq(),
    getGenesisTransactions,
    secretKey.publicImage(),
    new Signature25519(BytesUtils.fromHexString(
      "28f65fdffb6a0ecffd308445e1ef551935e614a45be9dc936467abcd82297fd5856a3395ae5854e13de9db576a88422da39970a93f0b21ba5b659b3f6cae0100")
    ),
    sidechainTransactionsCompanion
   )
  )*/
}

object SidechainSettings
  extends ScorexLogging
    with SettingsReaders
{

  val genesisParentBlockId : scorex.core.block.Block.BlockId = bytesToId(new Array[Byte](32))

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