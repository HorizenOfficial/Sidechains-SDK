package io.horizen.fixtures

import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.VrfPublicKey
import io.horizen.secret.VrfSecretKey
import io.horizen.utils.{MerklePath, Utils}
import io.horizen.utxo.block.{SidechainBlock, SidechainBlockHeader}
import io.horizen.utxo.fixtures.BoxFixture
import sparkz.util.bytesToId

import java.util.{ArrayList => JArrayList}
import scala.util.Random

trait SidechainBlockHeaderFixture extends BoxFixture {

  def createUnsignedBlockHeader(seed: Long = 123134L,
                                vrfKeysOpt: Option[(VrfSecretKey, VrfPublicKey)] = None,
                                vrfProofOpt: Option[VrfProof] = None,
                                blockVersion:Byte = SidechainBlock.BLOCK_VERSION): (SidechainBlockHeader, ForgerBoxGenerationMetadata) = {
    val random: Random = new Random(seed)

    val parentId = new Array[Byte](32)
    random.nextBytes(parentId)

    val (forgerBox, forgerMetadata) = ForgerBoxFixture.generateForgerBox(seed, vrfKeysOpt)
    val vrfProof = vrfProofOpt.getOrElse(VrfGenerator.generateProof(seed))

    val merklePath = new MerklePath(new JArrayList())

    val transactionsRootHash = Utils.ZEROS_HASH

    val mainchainRootHash = Utils.ZEROS_HASH

    val ommersRootHash = Utils.ZEROS_HASH


    val unsignedHeader = SidechainBlockHeader(
      blockVersion,
      bytesToId(parentId),
      random.nextInt(100) + 10000,
      forgerMetadata.forgingStakeInfo,
      merklePath,
      vrfProof,
      transactionsRootHash,
      mainchainRootHash,
      ommersRootHash,
      0,
      new Array[Byte](32),
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH))
    )

    (unsignedHeader, forgerMetadata)
  }
}
