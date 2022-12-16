package com.horizen.account.fixtures

import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.account.receipt.Bloom
import com.horizen.block.{SidechainBlock, SidechainBlockHeader}
import com.horizen.consensus.{ForgingStakeInfo, ForgingStakeInfoSerializer}
import com.horizen.fixtures.VrfGenerator
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.VrfSecretKey
import com.horizen.utils.{MerklePath, MerkleTree, Utils}
import scorex.util.bytesToId

import java.math.BigInteger
import java.util.{ArrayList => JArrayList}
import scala.util.Random

trait AccountBlockHeaderFixture {

  def createUnsignedBlockHeader(seed: Long = 123134L,
                                vrfKeysOpt: Option[(VrfSecretKey, VrfPublicKey)] = None,
                                vrfProofOpt: Option[VrfProof] = None,
                                blockVersion:Byte = AccountBlock.ACCOUNT_BLOCK_VERSION): (AccountBlockHeader) = {
    val random: Random = new Random(seed)

    val parentId = new Array[Byte](32)
    random.nextBytes(parentId)

    val vrfProof = vrfProofOpt.getOrElse(VrfGenerator.generateProof(seed))

    val merklePath = new MerklePath(new JArrayList())

    val transactionsRootHash = Utils.ZEROS_HASH

    val mainchainRootHash = Utils.ZEROS_HASH

    val ommersRootHash = Utils.ZEROS_HASH

    val forgingStakeInfo: ForgingStakeInfo = null // TODO
    val stateRoot = new Array[Byte](32) // TODO
    val receiptsRoot = new Array[Byte](32) // TODO
    val forgerAddress : AddressProposition = null // TODO
    val baseFee: BigInteger = BigInteger.ZERO // TODO
    val gasUsed: Long = 0; // TODO
    val gasLimit: Long = 0; // TODO
    val logsBloom: Bloom = null; // TODO



    val unsignedHeader = AccountBlockHeader(
      blockVersion,
      bytesToId(parentId),
      random.nextInt(100) + 10000,
      forgingStakeInfo,
      merklePath,
      vrfProof,
      transactionsRootHash,
      mainchainRootHash,
      stateRoot,
      receiptsRoot,
      forgerAddress,
      baseFee,
      gasUsed,
      gasLimit,
      ommersRootHash,
      0,
      new Array[Byte](32),
      logsBloom,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH))
    )

    (unsignedHeader)
  }
}
