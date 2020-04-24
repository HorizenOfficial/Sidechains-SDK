package com.horizen.datagenerator

import com.horizen.block._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.utils.BytesUtils
import java.math.BigInteger
import java.time.Instant

import com.horizen.block.{MainchainBlockReference, MainchainBlockReferenceSerializer, SidechainBlock}
import com.horizen.box.ForgerBox
import com.horizen.fixtures.{CompanionsFixture, ForgerBoxFixture, MerkleTreeFixture}
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.consensus._
import com.horizen.secret.VrfKeyGenerator
import org.junit.Test
import scorex.core.block.Block
import scorex.util.{ModifierId, bytesToId}


class sc_node_holder_fixter_settings extends CompanionsFixture {
  private val seed = 49850L

  @Test
  def generate_scGenesisBlockHex(): Unit = {
    val parentId: ModifierId = bytesToId(new Array[Byte](32))
    //val timestamp = 1574077098L
    val timestamp = Instant.now.getEpochSecond
    // Genesis MC block hex created in regtest from MC branch merge_support_branch on 04.05.2020
    // Command: zen-cli.exe -regtest sc_create "0000000000000000000000000000000000000000000000000000000000000001" 1000 "bf126e8d1db806f1b7d6ec11449c7c9218228c80675c8a46ebcef53b57c4ee40" 50 "ae2ac5544b91719ef675c6404d1f969840af4fe8ca67aa2c85555ffb96e351c58b3105cab56c7b0f171532110ab7ec1f879d7a1fe0467f273bf6dd0bc4e016f677289ccfa39d6604736d994c3201f3c0c9d4ac57d4e56859592291b8f8a45273"
    val mcBlockHex: String = "03000000749934fc0003b9a58b4ec224edba014abbc6eacdb85bb0bea37f9f6a0efaf20a6b88102abdf3b51ce57704e2d20d9b21b10b59403b17d2ddb6ea42019c90164e688a6c1790d383ced687150bdd29b818005239f908cdfaed1117204bb98acdbc55cbaf5e030f0f201000970d3013e3add5f230b8331886488c299e2a4f74c14a532254d302f0000024035bb1dd929da924b731a2673af35f3572ec13c0de8ff4d73dbdf71a2bd6dce24e324fc70201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dd000101ffffffff04611bb42c000000001976a914578f7b9829fd5c6cdf3203eb8a7f209505743a5e88ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff05f5939491a16bc8e791d8dade9e8755ed7fe0c059bc245689c38511d80978aeed000000006a47304402207528a1302f0757f2268d0349594d7b715b27f5b12481e48593b582f9dfbb7b84022028125c630b7864226b1f0a7e600fd9e4ec4ba2d8ff300c9e02ba4a1f09a79fe70121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffff24c09e3db567a0a14ab1765bc6331fc30187693cbd7f8a0935ed608a4849c049000000006b483045022100ae79e3eb7a696d8676a2313070491f6085831ac45084a516c083ec18d95b3f2402203158c34a880dee2cc6ed3b44dc484b0db58c924133538d5b19bc8685930330d90121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfefffffff6a8d248a72ddabea0a08041b83f5fa20062058dcc0ab0f9e71a091f93686c8d000000006a4730440220316ccaa2fd7ff9832f90c9e662f4a6f43381cdf6b85e770324334b32f7be4ea80220750b907edd68604e9c5631fbb25bfe3b827effb8782f4e1e3a3771d566e6c8a00121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffff88dbae6499670c0375c78fb5a9a61d14f02578696db56c22a0058293a46aced1000000006b483045022100ac2416d92ad17ce758f8e0a2c03db7c775ad1fbde9814399eb109623c4339e53022025e4584dccf3fc0aeebdddd43c4aff8b1b7276ee21cf124ea04848832bfe37b90121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffffd49753179fedf1b1f3d63a94287193381cf498e057d493d4c829b37e3c878ec2000000006b483045022100d7ab80769ccf6c4588af18179e03c9ee984d9e2af5342a263cda2bae28fa2516022073f62e6a4aaee17f22fe5f45e0e1dc51bfda80f811df85c4faa6ed7ce537a49f0121020742990624da99a58269923c2bf81e122f9253d2dd3b6e1cc0e2d952d52bccacfeffffff019f70d21a000000003c76a9145728a6a847dfcccc46dd37044d88ffa861779ed888ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010100000000000000000000000000000000000000000000000000000000000000e803000000f2052a0100000040eec4573bf5ceeb468a5c67808c2218927c9c4411ecd6b7f106b81d8d6e12bf60ae2ac5544b91719ef675c6404d1f969840af4fe8ca67aa2c85555ffb96e351c58b3105cab56c7b0f171532110ab7ec1f879d7a1fe0467f273bf6dd0bc4e016f677289ccfa39d6604736d994c3201f3c0c9d4ac57d4e56859592291b8f8a452730000d200000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001"))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params).get
    val mainchainBlockReferences = Seq(mcRef)
    val (forgerBox: ForgerBox, forgerMetadata)= ForgerBoxFixture.generateForgerBox(seed)
    val secretKey = VrfKeyGenerator.getInstance().generateSecret(seed.toString.getBytes)
    val publicKey = secretKey.publicImage()
    val genesisMessage =
      buildVrfMessage(intToConsensusSlotNumber(1), NonceConsensusEpochInfo(bigIntToConsensusNonce(BigInteger.valueOf(42))))
    val vrfProof: VrfProof = secretKey.prove(genesisMessage)
    val merklePath = MerkleTreeFixture.generateRandomMerklePath(seed + 1)
    val companion = getDefaultTransactionsCompanion

    val mainchainBlockReferencesData = mainchainBlockReferences.map(_.data)
    val mainchainHeaders = mainchainBlockReferences.map(_.header)

    val sidechainTransactionsMerkleRootHash: Array[Byte] = SidechainBlock.calculateTransactionsMerkleRootHash(Seq())
    val mainchainMerkleRootHash: Array[Byte] = SidechainBlock.calculateMainchainMerkleRootHash(mainchainBlockReferencesData, mainchainHeaders)
    val ommersMerkleRootHash: Array[Byte] = SidechainBlock.calculateOmmersMerkleRootHash(Seq())

    val blockVersion: Block.Version = 1: Byte

    val unsignedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
      blockVersion,
      parentId,
      timestamp,
      forgerBox,
      merklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      0,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH)) // empty signature
    )

    val signature = forgerMetadata.rewardSecret.sign(unsignedBlockHeader.messageToSign)

    val signedBlockHeader: SidechainBlockHeader = SidechainBlockHeader(
      blockVersion,
      parentId,
      timestamp,
      forgerBox,
      merklePath,
      vrfProof,
      sidechainTransactionsMerkleRootHash,
      mainchainMerkleRootHash,
      ommersMerkleRootHash,
      0,
      signature
    )

    val data = new SidechainBlock(signedBlockHeader, Seq(), mainchainBlockReferencesData, mainchainHeaders, Seq(), companion)

    val hexString = data.bytes.map("%02X" format _).mkString.toLowerCase
    println(hexString)
  }
}
