package com.horizen.account.fixtures

import com.horizen.account.block.{AccountBlock, AccountBlockHeader}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.Bloom
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.fixtures.VrfGenerator
import com.horizen.proof.{Signature25519, VrfProof}
import com.horizen.utils.{MerklePath, Utils}
import sparkz.util.bytesToId

import java.math.BigInteger
import java.util.{ArrayList => JArrayList}
import scala.util.Random

trait AccountBlockHeaderFixture {

  def createUnsignedBlockHeader(seed: Long = 123134L,
                                vrfProofOpt: Option[VrfProof] = None,
                                blockVersion:Byte = AccountBlock.ACCOUNT_BLOCK_VERSION): AccountBlockHeader = {
    val random: Random = new Random(seed)

    val parentId = new Array[Byte](32)
    random.nextBytes(parentId)

    val vrfProof = vrfProofOpt.getOrElse(VrfGenerator.generateProof(seed))
    val merklePath = new MerklePath(new JArrayList())
    val transactionsRootHash = Utils.ZEROS_HASH
    val mainchainRootHash = Utils.ZEROS_HASH
    val ommersRootHash = Utils.ZEROS_HASH

    val (accountPayment, forgerMetadata) = ForgerAccountFixture.generateForgerAccountData(0L)

    val forgingStakeInfo : ForgingStakeInfo = forgerMetadata.forgingStakeInfo
    val stateRoot = new Array[Byte](32)
    val receiptsRoot = new Array[Byte](32)
    val forgerAddress : AddressProposition = accountPayment.address
    val baseFee: BigInteger = BigInteger.ZERO
    val gasUsed: Long = 0
    val gasLimit: Long = 0
    val logsBloom: Bloom = new Bloom()



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

    unsignedHeader
  }
}
