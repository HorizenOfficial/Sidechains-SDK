package com.horizen.datagenerator

import com.horizen.block._
import com.horizen.params.{NetworkParams, RegTestParams}
import com.horizen.utils.BytesUtils
import java.time.Instant
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
    // Genesis MC block hex created in regtest from MC branch beta_v1 on 25.05.2020
    // Command: zen-cli.exe -regtest sc_create "0000000000000000000000000000000000000000000000000000000000000001" 1000 "a4465fd76c16fcc458448076372abf1912cc5b150663a64dffefe550f96feadd" 50 "3c158597376bccf305f7a363f7ba2b04e91e393eaedaa95a5e6284cd3607b58014a631e9e955dbdb2352a5b679e2d1269abeb59f81ef815d93280d8b46630b625f64e7c83e366c2bbd483ed0134322168ae8a6e1e0dd3d7f99ea038fe3df00003c29f7bdb83189a56e3ede091575f278b00d2754a842e0df517b8067304eada8b4bd31b3a51b4aa5365ebd35496102e8fb131684cb539d795f3b2624f84e0d82dc810075618c7b8259280ce14d8b03681ecc01fc69bb7bf0985b98140d06010000"
    val mcBlockHex: String = "030000002905b49f5bcd0b8e29f91170467e6900a82f4ec9a51e6d6ff12bfd4156b6110191fdc3a11d8a7014e3cf25197a56d3e0199ec92fc70cd0e91d49ff7c270fb435ae628657d19a111490839a6a66c4dd09a422c94a2a486e411d0fe54e858eb353afd4cb5e030f0f2017006d83d7e2ebd3c45ad11831e620d5f8b7b5c4f4c912cdc9f7c33a5daa0000240975303f82ac016f1418a4d3f6151fc753c11ed498d635ee2b1fc22a2e4e370306b14ee10201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dd000101ffffffff04c21bb42c000000001976a914c5be5df5a7b85620af7324891f4107adf2e05a7888ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff0523e3e0ffb44e0084187db48ad78b854b034271e0d52de1db710455875bc2d9d5000000006b483045022100b7146011794140b618cd99b026f2bdb4b7a3f8b5850748583714793a5e6dd8ed02203cc86851619f0219a51b24df0120ed9efa8fb708ac0f27a178789af0304a63f70121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff3e3b7f63715ddd6451de91104c503c5ce0400151eed36e24f9d4fbfda7ed77ab000000006b483045022100a83e9e5e2736909805015058b7b8f1926f22d879e674f08f2bfd798b441bbcff022055eb00442e872fd03fb3399c48b4f5b6ac72d2d42ad098b9f51fa1b0696c8c2c0121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff714dcef46d1b8944f8562cd8fc528c15474938f2f88cd2a94964c4d17970379c000000006a473044022002eb0b187e5335c946699fa66177bfa8bfc6e426fc0606bc304e9bb11e2a66b902206dca231c7cb52289ef2bd0333d4ebc1324c03587e9d6d60176aff20f44425acc0121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffffff4646d63099c44b1e5be6dfc2ab8cb3f2db6bd55f49eade7b25dc138a55417d000000006a4730440220766b9fc27888c9ebb3f54d5e2c27b6cb93789c1d8682052944d567c6bacff7ab02201a0e220d86a33f9f83ded0dd166b0152a3180d2c563eb9946ee24dbdb2768fb80121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff2b02c1d304db30119d69fa07751b386ee2e9019aad8cc0abdda217ed674244bc000000006b483045022100ff309d03234ca8afe7e5a79e406590746c47043736e44b44ed21d15cc302999602204712d0dda3fa5f65f834acbfdc7e76f97878a3b732a74623f4efacff915786680121032fbe16ef65339e089124dbc24fe58cf8b3ca10340bae82ffdf57520f7ff48402feffffff013e70d21a000000003c76a91457aa4f67e7d37b86d7c75b8bde5c1124a64dc30d88ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010100000000000000000000000000000000000000000000000000000000000000e803000000f2052a01000000ddea6ff950e5efff4da66306155bcc1219bf2a3776804458c4fc166cd75f46a4c13c158597376bccf305f7a363f7ba2b04e91e393eaedaa95a5e6284cd3607b58014a631e9e955dbdb2352a5b679e2d1269abeb59f81ef815d93280d8b46630b625f64e7c83e366c2bbd483ed0134322168ae8a6e1e0dd3d7f99ea038fe3df00003c29f7bdb83189a56e3ede091575f278b00d2754a842e0df517b8067304eada8b4bd31b3a51b4aa5365ebd35496102e8fb131684cb539d795f3b2624f84e0d82dc810075618c7b8259280ce14d8b03681ecc01fc69bb7bf0985b98140d0601000000d200000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000001"))
    val mcRef: MainchainBlockReference = MainchainBlockReference.create(BytesUtils.fromHexString(mcBlockHex), params).get
    val mainchainBlockReferences = Seq(mcRef)
    val (forgerBox: ForgerBox, forgerMetadata)= ForgerBoxFixture.generateForgerBox(seed)
    val secretKey = VrfKeyGenerator.getInstance().generateSecret(seed.toString.getBytes)
    val publicKey = secretKey.publicImage()
    val genesisMessage =
      buildVrfMessage(intToConsensusSlotNumber(1), NonceConsensusEpochInfo(ConsensusNonce @@ "42".getBytes))
    val vrfProof: VrfProof = secretKey.prove(genesisMessage).getKey
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

    val signature = forgerMetadata.blockSignSecret.sign(unsignedBlockHeader.messageToSign)

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
