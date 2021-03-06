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
    // Genesis MC block hex created in betav1 from MC branch add_attributes_snark on 12.06.2020
    //related data: see examples/simpleapp/src/main/resources/sc_settings.conf
    val mcBlockHex: String = "030000001b199ec4516c4b6021363cd73dcadd5382a1281de6291a46d8a0a6b01a4d490529707a00647aac4ba0f00f64b3b46f6f2d496a030cc23b5808ad78d1fa3606d73622107ce90ea52de0b1b5896459f9b41d0b83076920494643229a5d320a78e6c149e35e030f0f202200b5e4f98dd21ec442183f8bc8ec9a2e483da99e3a773c50390302d7570000240140d14c11a99d48f9244edc0f735729897402236d7a82d9b63fb519d04ab512a2bb39b20201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0502dc000101ffffffff045724b42c000000001976a914a6dcdfc99033d07beb459e5d14fc903296e0cc6e88ac80b2e60e0000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f587405973070000000017a914b6863b182a52745bf6d5fb190139a2aa876c08f58700000000fcffffff09174cdf38ee44a2d9ea76363c5f121afadc69dfc7d9d0c4ab0e35e6970eb99108000000006a4730440220612dba5d3968c27a9d162863f4a30d0ee597e3d1628da6a854fd3020eef66a67022034a37a3fa5f1fecd587e74a3f5d3d9f86af1e4abece4ed4c24bf106d3b0d1c0b012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff64a2205070fe9747fb641b1614027b8bf1fa18a95cc808b05cb5206dc27e66ca000000006b48304502210093a89f7818dd71469ba6d2427f6a7cd59a8502d94b4c90f3eba88e722e1e94c302207625ae9a3c1a5ddb7ade8ade70eb89d772743fcb3b4e79af43bb24b9c66d104e012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffe8ac048bfa7286d87756857d4d3a78291cba719c63a32c070982a5fc2d226e1c000000006a473044022038c5996d46c02269d435db49201ec6dc371e2fe873c258a6fe768bf71ffd00fc02200413e4610c064bbbac529f8d3bb68f118814b00cf6d362af2a866535e149ffc3012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff9c1192ad64c623a0e30edf4cc38e8122f5b47f0c5ce954555999bbcc6d54cb84000000006b483045022100c61768c9315bc074dc089bb74002e2e63cf92b22b6acf9e6200a43bb755825e8022008570a0e68a2bd4da4fee3686efff28a5e65902924571ace2475e256fa23f15e012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff04d9f29d257d289b5693a0da5bf9c01e620b4de046b918f92b22ec3ae2c166fd000000006a4730440220156bc610578fb1c5f8da019760a24c0634e3536225ec4b7f6aa89ad8c56a72f602202034a699c347baaaed72fbbe8f9cb453d9d72fa1e44e2623c6e19ddf0a92da6a012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff97c2f86e46e775b320e22630dbe84e7a04f5103bb1b7f54797bd76ba843ca44e000000006a473044022071b9c641218c8153e855615996803cd0885dc7b0826b1aab3cb4c4c9be638f64022064c3e1a5b22ce38ae566c1f73924666b49876e06c283529a8875d9e74be0300c012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffd1d57576ba121a45e4ad340975abdebf24d0d0dab85dd17440d78aaf13cc0b38000000006a47304402202fd572e2c941feb8710283a6e95a8d39964f01873cf7506dcdc828d914ac4f4302204d0b6bcf9d05e902e9a05cc96d41c2a19af0bf1055177f8e1111b2a8e2e5de2b012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffffce4e0c6cd8f1c1b2ab2350d72383aaefe8d925128b126d40d7ef346910a6ea8a000000006a47304402205e2bd14ea3787febea21803a135a882b553fff7758fc2370af8055c1694d8ba3022034382608166beaa057324ecdaa0f6747022f2e0f7f2b3539426b5f62440beb67012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff3d71485da571aacb77f33117e4aa59cf02eec6ad38210c110ca09e9258df1b33000000006b48304502210084dcc8d11fba1b4b2c2b74cca2128b40bdc563ed00a2d725144117b7f3b29c34022074edb63dd8422649237d421e32b6aa3d8db75c0f8bcdf3a809ea1b003621de96012102f0801da5b557dde40d9bd022362ef99752919517cd4ce5964dcada9175a1e8b5feffffff01696b7d01000000003c76a9144129792b8ec0606c8ddb53a8c011f257dd2292e288ac20bb1acf2c1fc1228967a611c7db30632098f0c641855180b5fe23793b72eea50d00b4010a00000000e40b5402000000acb24b2f08e8e56fe1784f1600879c697c7cd90846e076724b090fd72206b1a5c1dd2de641154fd54de4cf60ea3f5b9e7135787ecb9fcce75de5c41f974fd0cbf70af51ba99b1b8d591d237091414051d2953b7d75e16d89be6fe1cf0bfc63a244f6f51159061875ff1922c3d923d365370ac2605c19e03d674bf64af9e91e00003a6fe5d3f1bcddf09faee1866e453f99d4491e68811bc1a7d5695955e4f8f456627f546bdbbbd026c1b6ee35e2f65659cbcd32406026ebb8f602c86d3f42499f8412dc3ebe664ce188c69360f13dddbd577513171f49423d51ff9578b15901000060b866a7695601aa41cb7775ed16208d0f79e8c19376d99b3cbab937f3a271a7347a9eceab34d14ddcd0aa7936869cc620d5f5a8a44c78a0fc621edfc69e2751ad4192fd57a57eb5d8e798bd15773b20a29cd260755b6c5ec7b051ecd4685e01005e7b462cc84ae0faaa5884bd5c4a5a5edf13db210599aeeb4d273c0f5f32967b7071ce2b4d490b9f08f6ce66a8405735c79197cd6773d1c5aeb2a38da1c102df07b05879c77198e5aafa7feed25d4137e86b3d98d9edd9547a460f1615b10000ee9570fbffedd44170477b37500a0a1cb3f94b6361f10f8a68c4075fbc17542d7174b3d95e12ddb8aea5d6b6c53c1df6c8f60010cd2e69902ba5e89e86747569463a23254730fc8d2aabf39648a505df9dcce461443b181ef3eda46074070000550836db2c97820971db6b1421e348d946ed4d3f255295abea46556615e3123de33ec56f784f70302901a4bc10c79c6a8b1e32477aeff9fba75876592981b678fc5a2703ac0b3055e567a6cb1ebab578fc4f9121fd968680250696cb85790000078fcfb60bdfc79aa1e377cb120480538e0236156f23129a88824ca5a1d77e371e5e98a16e6f32087c91aa02a4f5e00e412e515c3b678f6535141203c6886c637b626a2ada4062d037503359a680979091c68941a307db6e4ed8bc49d21b00002f0e6f88fb69309873fdefb015569e5511fb5399295204876543d065d177bf36ab79183a7c5e504b50691bc5b4ed0293324cfe2555d3fc8e39485822a90a91afcd4ef79ec3aefbd4cbe25cbccd802d8334ce1dce238c3f7505330a14615500001f89fbe1922ab3aa31a28fd29e19673714a7e48050dee59859d68345bb7bee7d5e888d8b798a58d7c650f9138304c05a92b668294c6114185ccb2c67ce0bbbb7e1dcbb6d76f5cacd7c9732a33b21d69bd7a28c9cca68b5735d50413862bc0100308bb0dd0bd53f3d1134966702dd3c7cc8b58b270a6996a646493250b0d5f3978d0c971f8fa7a0c958f3efe2fa5269244973fafb701c2eb66dd25901f93d677ab6c538c1ed11f115e52d3f2c7087ea40c3e8cd089376baa38842e9429b5f0000d19a8d874d791f952f13d3c8ecd92e44009c09815e5ae6a8e5def7ea52fe3de4accfb5ba2aa401fbcec14b069cd0dc0f66ab025b45ef9831a26acf58673db7487043654e7980fcb2b6c1bd7593a4dfff810436f653e309121c7ccf2df70b010000732254ec6df184be360cd9ed383ed7c8c236d7761cfc0ce4e7f0cac5a06f4edab9cfc75a7dc1449c0e18ed9564c974c2e1b6847c637f74e5d391cbc80fc6e672ffd66b5ce4fb73bda8359ab8a0ea1e855df1e07d82f93c935c7e1a9a55c5000065efdbb7c3e82291a482b2f24cbd46f4dd02c370cf6dcfe8fb3c00b8b004b5ad51369b1f1b134a824d1f16d72ca6a27ba2d6190150329139cf2c6d9e5a14722f8d39b96b882c1f60a7b230e929819e2abe1cd9d7f3e8c726b1a94d20c8010100732c396eca6ffa1bf851cef449f2f087edd93e4f641b4bd93a482d9f129e675aedb688993d4e2cee824d2803301364ba10fbb66895927adb53bad8aefe8a1caab6f4ccb45883e414a1223ac7f90a89087cd752dfa0c7b3e19bbae000edd5000028d1d23c627d1252d2a2a20a246af2280f50e3fde667873aadd9893ba6833118358398e7428e717128f764714a8d52b090c1f554f58e25ea815338d7bc7326c949567e74f2f2ab3c88f5075fea75594608b8937c9059a42d712ffbd1bd980100000000000250c1a474689e375a309446e5cdd3a0c26cecdcff5c7b8cdc0728868983f1a35a49e3a1bae6f969c3d47356c08d3d169d2c0a2be908d82cd35f41a23d8c2924a9f790ab3a00d53061d440a176670d6a32de2ecd19cf8a9774729c09a6ea4d0100d8838bf55d95521291da12294b302c66042eda0dc2acc79360a1fdd8c9a366fa790c52bf926c2d96b5ba88a3a443487c5235f7c476f350c2101cfbe3bd0361dd291ebc5e42c097a158704b71006886a3662ca6db7d816b4ad12444835d89000000795ce2b34aef921ccb3d9b9695f5d3fe0a03743c955cfcf01f8a1815a7c8b03de85fe15201d4b4b6f401cb334a6988ea5bde8986a468c47c3c6a5ae96a3160ff15e06699ea82bd40c0d5547fe1be77af7817861bbfcca3f4232f05a9cec800006c216565cee4d57b32d2d70bb3cb8d4a967c0eb5d7137b2ec58466f3d4d3b5375e4baa823bcc29c6ad877d9708cd5dc1c31fa3883a80710431110c4aa22e97b67fa639f54e86cfab87187011270139df7873bed12f6fb8cd9ab48f389338010000007500000000"
    val params: NetworkParams = RegTestParams(sidechainId = BytesUtils.fromHexString("2f7ed2e07ad78e52f43aafb85e242497f5a1da3539ecf37832a0a31ed54072c3"))
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
      forgerMetadata.forgingStakeInfo,
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
      forgerMetadata.forgingStakeInfo,
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
