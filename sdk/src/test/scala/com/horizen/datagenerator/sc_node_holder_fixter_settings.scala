package com.horizen.datagenerator

import com.horizen.block._
import com.horizen.box.ForgerBox
import com.horizen.fixtures.{CompanionsFixture, ForgerBoxFixture, MerkleTreeFixture}
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.proof.Signature25519
import com.horizen.utils.BytesUtils
import com.horizen.vrf.{VRFKeyGenerator, VRFProof}
import org.junit.Test
import scorex.core.block.Block
import scorex.util.{ModifierId, bytesToId}


class sc_node_holder_fixter_settings extends CompanionsFixture {
  private val seed = 49850L

  @Test
  def generate_scGenesisBlockHex(): Unit = {
    val parentId: ModifierId = bytesToId(new Array[Byte](32))
    val timestamp = 1574077098L
    // Genesis MC block hex created in regtest from MC branch as/sc_development on 25.03.2020
    val mcBlockHex: String = "00000020bb9fd9588dd8523bfa077ff0adb0dab8d78b3619c0e9b742dd3e58e0402cdf0de2059e13e19f90104e38944aa33ba68891723ae2445b07eb0f2aa28669d2764a44ab95842c22d16bfa1333cd9e6695d69d7ca3f4b3eecc94b92dbd6c2fa4798c9c547b5e030f0f201600d0dbd34fc3b2011d6db2886e0c6e7cf7c21797a0e5c4e913c2ac3db800002405a66039a239bcd8b90b9d0bf3e6154219bf1668a6f4525f0e276651d5d58ecd9f4797f50201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dd000101ffffffff048e1cb42c000000001976a914d97b86863cf44a73045c3e4111fc9c7be16bbe3888ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff075d77e3aa567b01147de9e3e33e86d76ea5124bca65ffa8870ec2d01863327627000000006a47304402200f14c80cf0ed7670f02d3b262d02af51c6bb33bdf30ff41e3bbc2adac78291200220717d61da21ab02f4776986697cc5a1440a04775460e80d5ba6a536efe5e7324e01210326bd17d022ee50b935a801847e50b2de4084837ed1c9de44f0b3ff4d90021c11feffffff2fbd581f0640efa9452654dee92ea7c99693668e86e060090e952d65b34c6b86000000006a47304402202fb8fd8f3ebfd2cfb1a8954cf1f640eec72e85d5e335e01749a3b6fd6c54a1a002203f67c0e7da34bcca06bf594c362787fe0a57fca6b48d77064f6210ccbdb294f701210326bd17d022ee50b935a801847e50b2de4084837ed1c9de44f0b3ff4d90021c11feffffff4b40cabbb11f1942917ba73ac5e3a94a6b1a0561026869ef18230991fef02f63000000006b48304502210082ddf622c83ab49def9045a682ce5fc03831ff617a49590ae768103c9922d8fc02206768fe9da2ce75ed669ed97e8dc3749cecf51f8a61c6d48db458d141db99459301210326bd17d022ee50b935a801847e50b2de4084837ed1c9de44f0b3ff4d90021c11feffffffbd00c4acb615d8a19dfc4582f8a85594f0ad55c6f9cbb38056aec6a2ecbbd8dd000000006a4730440220380c4010cadd26e763c55b025938c1f023b7b4100549653bda00e6ccc0e60fff02202fe3d0d8992a0e57f5516f1eaac95123ba0a2cc0abc43e32bae9fc8943650e9301210326bd17d022ee50b935a801847e50b2de4084837ed1c9de44f0b3ff4d90021c11feffffffad8fcb83ca67b752caaa70240cb937720e705b21f2fd3e57898f0ef2e71acc25000000006a47304402206c60e934c5251c7554ad85817fcd794ffcd77bdaac82672d8e18b742f7bbd316022044893f1a7406df845ea37ea61c8f7b68730ce1b221aef9fa5bd37ba783ad23b701210326bd17d022ee50b935a801847e50b2de4084837ed1c9de44f0b3ff4d90021c11feffffffdb9cce1d1db10f2c25ce9956f6ff7115eb699bbf6423d8b22097b4c33105a196000000006a473044022059c861b5b7d46e56dfeba799a00641c52f43c1ae9a4f0bf0294ccc7c30f8fcf202205df41b13755465ce53733941fc3332173feb187d5c02b085f6f3acfbd13db61601210326bd17d022ee50b935a801847e50b2de4084837ed1c9de44f0b3ff4d90021c11feffffffac15c282cda174b40064642096bce4a201e12fe3fe3319c9e3b80d9f5b0cd3ea000000006a473044022048d3f8d467013cc3e2d37df43cba5df7c84ed677a1f78fcd6e9cc6795027ed9a0220509193ea449671993d50b0e825159d57bc35653b9d935fb691b0b7057e50ed5c01210326bd17d022ee50b935a801847e50b2de4084837ed1c9de44f0b3ff4d90021c11feffffff0102595f00000000003c76a914ce75ce5712b16adda668cfa5f712e8ad90d14f5c88ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010100000000000000000000000000000000000000000000000000000000000000e80300000002005ed0b20000000040eec4573bf5ceeb468a5c67808c2218927c9c4411ecd6b7f106b81d8d6e12bf010000000000000000000000000000000000000000000000000000000000000000f2052a01000000163076d7df8356a0d2322d8883651d1fe585bfb59c52104e87bcac5a03af711d0100000000000000000000000000000000000000000000000000000000000000d2000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001"))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params).get
    val mainchainBlockReferences = Seq(mcRef)
    val (forgerBox: ForgerBox, forgerMetadata)= ForgerBoxFixture.generateForgerBox(seed)
    val (secretKey, publicKey) = VRFKeyGenerator.generate(seed.toString.getBytes)
    val vrfMessage: Array[Byte] = "!SomeVrfMessage1!SomeVrfMessage2".getBytes
    val vrfProof: VRFProof = secretKey.prove(vrfMessage)
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
