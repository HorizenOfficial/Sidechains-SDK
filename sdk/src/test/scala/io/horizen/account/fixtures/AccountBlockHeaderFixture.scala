package io.horizen.account.fixtures

import io.horizen.account.block.{AccountBlock, AccountBlockHeader}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.utils.Bloom
import io.horizen.account.utils.FeeUtils.{GAS_LIMIT, INITIAL_BASE_FEE}
import io.horizen.consensus.ForgingStakeInfo
import io.horizen.fixtures.VrfGenerator
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.VrfPublicKey
import io.horizen.secret.VrfSecretKey
import io.horizen.utils.{MerklePath, Utils}
import io.horizen.vrf.VrfOutput
import sparkz.util.bytesToId

import java.math.BigInteger
import java.util.{ArrayList => JArrayList}
import scala.util.Random

trait AccountBlockHeaderFixture {

  def createUnsignedBlockHeader(seed: Long = 123134L,
                                vrfKeysOpt: Option[(VrfSecretKey, VrfPublicKey)] = None,
                                vrfProofOpt: Option[VrfProof] = None,
                                vrfOutputOpt: Option[VrfOutput] = None,
                                blockVersion:Byte = AccountBlock.ACCOUNT_BLOCK_VERSION): (AccountBlockHeader,  ForgerAccountGenerationMetadata) = {
    assert(vrfProofOpt.isDefined == vrfOutputOpt.isDefined, "VRF proof and output must be both defined or not")
    val random: Random = new Random(seed)

    val parentId = new Array[Byte](32)
    random.nextBytes(parentId)

    val (accountPayment, forgerMetadata) = ForgerAccountFixture.generateForgerAccountData(seed, vrfKeysOpt)
    val proofAndOutput = VrfGenerator.generateProofAndOutput(seed)
    val vrfProof = vrfProofOpt.getOrElse(proofAndOutput.getKey)
    val vrfOutput = vrfOutputOpt.getOrElse(proofAndOutput.getValue)
    val merklePath = new MerklePath(new JArrayList())
    val transactionsRootHash = Utils.ZEROS_HASH
    val mainchainRootHash = Utils.ZEROS_HASH
    val forgingStakeInfo : ForgingStakeInfo = forgerMetadata.forgingStakeInfo
    val stateRoot = new Array[Byte](32)
    val receiptsRoot = new Array[Byte](32)
    val forgerAddress : AddressProposition = accountPayment.address
    val baseFee: BigInteger = INITIAL_BASE_FEE
    val gasUsed: BigInteger = BigInteger.valueOf(21000)
    val gasLimit: BigInteger = GAS_LIMIT
    val ommersRootHash = Utils.ZEROS_HASH
    val ommersCumulativeScore : Long = 0L;
    val feePaymentHash = new Array[Byte](32)
    val logsBloom: Bloom = new Bloom()

    val unsignedHeader = AccountBlockHeader(
      blockVersion,
      bytesToId(parentId),
      random.nextInt(100) + 10000,
      forgingStakeInfo,
      merklePath,
      vrfProof,
      vrfOutput,
      transactionsRootHash,
      mainchainRootHash,
      stateRoot,
      receiptsRoot,
      forgerAddress,
      baseFee,
      gasUsed,
      gasLimit,
      ommersRootHash,
      ommersCumulativeScore,
      feePaymentHash,
      logsBloom,
      new Signature25519(new Array[Byte](Signature25519.SIGNATURE_LENGTH))
    )

    (unsignedHeader, forgerMetadata)
  }
}
