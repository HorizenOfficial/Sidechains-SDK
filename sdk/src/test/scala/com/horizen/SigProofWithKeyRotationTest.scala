package com.horizen

import com.google.common.io.Files
import com.horizen.block.{FieldElementCertificateField, SidechainCreationVersions, WithdrawalEpochCertificate}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.box.data.WithdrawalRequestBoxData
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignatures
import com.horizen.certnative.BackwardTransfer
import com.horizen.cryptolibprovider.implementations.{SchnorrFunctionsImplZendoo, ThresholdSignatureCircuitWithKeyRotationImplZendoo}
import com.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider}
import com.horizen.fixtures.{FieldElementFixture, SecretFixture}
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.{MCPublicKeyHashProposition, SchnorrProposition}
import com.horizen.secret.SchnorrSecret
import org.junit.Assert.{assertTrue, fail}
import org.junit.{After, Ignore, Test}
import java.io._
import java.util.Optional
import java.{lang, util}
import scala.collection.JavaConverters._
import scala.util.Random

class SigProofWithKeyRotationTest extends SecretFixture {
  private val sigCircuit: ThresholdSignatureCircuitWithKeyRotationImplZendoo = new ThresholdSignatureCircuitWithKeyRotationImplZendoo()
  private val schnorrFunctions: SchnorrFunctionsImplZendoo = new SchnorrFunctionsImplZendoo()

  private val tmpDir: File = Files.createTempDir
  private val provingKeyPath: String = tmpDir.getAbsolutePath + "/snark_pk_with_key_rotation"
  private val verificationKeyPath: String = tmpDir.getAbsolutePath + "/snark_vk_with_key_rotation"

  @After
  def cleanup(): Unit = {
    new File(provingKeyPath).delete()
    new File(verificationKeyPath).delete()
    tmpDir.delete()
  }


  //Test will take around 2 minutes, enable for sanity checking of ThresholdSignatureCircuitWithKeyRotation
  @Ignore
  @Test
  def simpleCheck(): Unit = {
    val keyPairsLen = 4
    val threshold = 3 //hardcoded value

    val signerKeyPairs = (0 until keyPairsLen).view.map(buildSchnorrPrivateKey).map(secret => (secret, secret.getPublicKey))
    val masterKeyPairs = (keyPairsLen until 2*keyPairsLen).view.map(buildSchnorrPrivateKey).map(secret => (secret, secret.getPublicKey))
    val signerPublicKeysBytes: util.List[Array[Byte]] = signerKeyPairs.map(_._2.serializePublicKey()).toList.asJava
    val masterPublicKeysBytes: util.List[Array[Byte]] = masterKeyPairs.map(_._2.serializePublicKey()).toList.asJava

    val sysConstant = sigCircuit.generateSysDataConstant(signerPublicKeysBytes, masterPublicKeysBytes, threshold)

    val epochNumber0: Int = 0
    val btrFee: Long = 100
    val ftMinAmount: Long = 100
    val endCumulativeScTxCommTreeRoot = FieldElementFixture.generateFieldElement()
    val sidechainId = FieldElementFixture.generateFieldElement()
    val genesisKeyRootHash = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateKeysRootHash(signerPublicKeysBytes, masterPublicKeysBytes)

    val bt: util.List[BackwardTransfer] = CommonCircuit.getBackwardTransfers(Seq(new WithdrawalRequestBox(new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte)), 2345), 42)).asJava)

    // Try with no key updates and first epoch
    val keysRootHash = genesisKeyRootHash.clone() // key root hash remains the same as the genesis one

    val messageToBeSignedForEpoch0 = sigCircuit.generateMessageToBeSigned(bt, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, keysRootHash)

    val emptySigs = List.fill[Optional[Array[Byte]]](keyPairsLen - threshold)(Optional.empty[Array[Byte]]())
    val signaturesForEpoch0: util.List[Optional[Array[Byte]]] = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedForEpoch0)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    // Setup proving system keys
    println(s"Generating Marlin dlog key.")
    if (!CryptoLibProvider.commonCircuitFunctions.generateCoboundaryMarlinDLogKeys()) {
      fail("Error occurred during dlog key generation.")
    }

    println(s"Generating Marlin snark keys. Path: pk=$provingKeyPath, vk=$verificationKeyPath")
    if (!CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateCoboundaryMarlinSnarkKeys(keyPairsLen, provingKeyPath, verificationKeyPath)) {
      fail("Error occurred during snark keys generation.")
    }

    println("Generating snark proof...")
    val emptyUpdateProofs = List.fill[Option[SchnorrProof]](keyPairsLen)(Option.empty[SchnorrProof])

    var schnorrKeysSignatures = SchnorrKeysSignatures(
      signerPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      masterPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      signerPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      masterPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      emptyUpdateProofs,
      emptyUpdateProofs,
      emptyUpdateProofs,
      emptyUpdateProofs
    )

    var proofAndQuality: utils.Pair[Array[Byte], lang.Long] = sigCircuit.createProof(bt, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, signaturesForEpoch0, schnorrKeysSignatures,
      threshold, Optional.empty(), 2, genesisKeyRootHash, provingKeyPath, true, true)


    var result = sigCircuit.verifyProof(bt, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, keysRootHash,
      threshold, Optional.empty(), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)


    // Try with no key updates and not a first epoch certificate
    val epochNumber10 = 10
    val messageToBeSignedForEpoch10 = sigCircuit.generateMessageToBeSigned(bt, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, keysRootHash)
    val signaturesForEpoch10 = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedForEpoch10)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava


    var prevCustomFields: Seq[Array[Byte]] = sigCircuit.getCertificateCustomFields(genesisKeyRootHash).asScala
    val prevEndCumulativeScTxCommTreeRoot = FieldElementFixture.generateFieldElement()

    var prevEpochCertificate = WithdrawalEpochCertificate(
      Array[Byte](),
      keyPairsLen,
      sidechainId,
      epochNumber10 - 1,
      threshold.asInstanceOf[Long] - 1,
      Array[Byte](),
      prevCustomFields.map(cf => FieldElementCertificateField(cf)),
      Seq(),
      prevEndCumulativeScTxCommTreeRoot,
      btrFee,
      ftMinAmount,
      Seq(),
      Seq(),
      Seq()
    )

    var prevCertificate = CommonCircuit.createWithdrawalCertificate(prevEpochCertificate, SidechainCreationVersions.SidechainCreationVersion2)

    System.out.println("Generating snark proof...")
    proofAndQuality = sigCircuit.createProof(bt, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, signaturesForEpoch10, schnorrKeysSignatures,
      threshold, Optional.of(prevEpochCertificate), 2, genesisKeyRootHash, provingKeyPath, true, true)

    result = sigCircuit.verifyProof(bt, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, keysRootHash,
      threshold.asInstanceOf[Long], Optional.of(prevCertificate), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)


    // Try with signer key updates and first epoch
    val newSigningKeySecretKey = buildSchnorrPrivateKey(2*keyPairsLen)
    val newSigningKeyPublicKey = newSigningKeySecretKey.getPublicKey
    val newKeyToSign = newSigningKeyPublicKey.getHash.serializeFieldElement()

    val updatedSignerPublicKeysBytes: util.ArrayList[Array[Byte]] = new util.ArrayList[Array[Byte]]()
    val updatedMasterPublicKeysBytes: util.ArrayList[Array[Byte]] = new util.ArrayList[Array[Byte]]()
    updatedSignerPublicKeysBytes.add(newSigningKeyPublicKey.serializePublicKey())
    updatedMasterPublicKeysBytes.add(newSigningKeyPublicKey.serializePublicKey())
    for (i <- 1 until signerPublicKeysBytes.size()) {
      updatedSignerPublicKeysBytes.add(signerPublicKeysBytes.get(i))
      updatedMasterPublicKeysBytes.add(masterPublicKeysBytes.get(i))
    }

    val oldSignerSignature = new SchnorrSecret(signerKeyPairs.head._1.serializeSecretKey(), signerKeyPairs.head._2.serializePublicKey()).sign(newKeyToSign).bytes()
    val oldMasterSignature = new SchnorrSecret(masterKeyPairs.head._1.serializeSecretKey(), masterKeyPairs.head._2.serializePublicKey()).sign(newKeyToSign).bytes()

    val signerSignatures = new util.ArrayList[Option[SchnorrProof]]()
    signerSignatures.add(Option.apply(new SchnorrProof(oldSignerSignature)))
    val masterSignatures = new util.ArrayList[Option[SchnorrProof]]()
    masterSignatures.add(Option.apply(new SchnorrProof(oldMasterSignature)))
    for(_ <- 0 until keyPairsLen-1) {
      signerSignatures.add(Option.empty)
      masterSignatures.add(Option.empty)
    }

    println("Generating snark proof...")

    schnorrKeysSignatures = SchnorrKeysSignatures(
      signerPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      masterPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      updatedSignerPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      masterPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      signerSignatures.asScala,
      masterSignatures.asScala,
      emptyUpdateProofs,
      emptyUpdateProofs
    )

    var newKeysRootHash = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateKeysRootHash(updatedSignerPublicKeysBytes, masterPublicKeysBytes)

    val messageToBeSignedWithRotationEpoch0 = sigCircuit.generateMessageToBeSigned(bt, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, newKeysRootHash)

    val signaturesWithRotationEpoch0: util.List[Optional[Array[Byte]]] = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedWithRotationEpoch0)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    proofAndQuality = sigCircuit.createProof(bt, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, signaturesWithRotationEpoch0, schnorrKeysSignatures,
      threshold, Optional.empty(), 2, genesisKeyRootHash, provingKeyPath, true, true)


    result = sigCircuit.verifyProof(bt, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, newKeysRootHash,
      threshold.asInstanceOf[Long], Optional.empty(), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)


    // Try with master key updates and no first epoch

    println("Generating snark proof...")

    schnorrKeysSignatures = SchnorrKeysSignatures(
      signerPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      masterPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      signerPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      updatedMasterPublicKeysBytes.asScala.map(b => new SchnorrProposition(b)),
      emptyUpdateProofs,
      emptyUpdateProofs,
      signerSignatures.asScala,
      masterSignatures.asScala
    )

    newKeysRootHash = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateKeysRootHash(signerPublicKeysBytes, updatedMasterPublicKeysBytes)
    prevCustomFields = sigCircuit.getCertificateCustomFields(genesisKeyRootHash).asScala

    prevEpochCertificate = WithdrawalEpochCertificate(
      Array[Byte](),
      keyPairsLen,
      sidechainId,
      epochNumber10 - 1,
      threshold.asInstanceOf[Long] - 1,
      Array[Byte](),
      prevCustomFields.map(cf => FieldElementCertificateField(cf)),
      Seq(),
      prevEndCumulativeScTxCommTreeRoot,
      btrFee,
      ftMinAmount,
      Seq(),
      Seq(),
      Seq()
    )

    prevCertificate = CommonCircuit.createWithdrawalCertificate(prevEpochCertificate, SidechainCreationVersions.SidechainCreationVersion2)

    val messageToBeSignedWithRotationEpoch10 = sigCircuit.generateMessageToBeSigned(bt, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, newKeysRootHash)

    val signaturesWithRotationEpoch10: util.List[Optional[Array[Byte]]] = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedWithRotationEpoch10)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    proofAndQuality = sigCircuit.createProof(bt, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, signaturesWithRotationEpoch10, schnorrKeysSignatures,
      threshold, Optional.of(prevEpochCertificate), 2, genesisKeyRootHash, provingKeyPath, true, true)

    result = sigCircuit.verifyProof(bt, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, newKeysRootHash,
      threshold.asInstanceOf[Long], Optional.of(prevCertificate), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)
  }
}
