package com.horizen

import com.google.common.io.Files
import com.horizen.block.{FieldElementCertificateField, WithdrawalEpochCertificate}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.box.data.WithdrawalRequestBoxData
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes.getSchnorrKeysSignaturesList
import com.horizen.cryptolibprovider.implementations.{SchnorrFunctionsImplZendoo, ThresholdSignatureCircuitWithKeyRotationImplZendoo}
import com.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider}
import com.horizen.fixtures.FieldElementFixture
import com.horizen.proposition.{MCPublicKeyHashProposition, SchnorrProposition}
import com.horizen.schnorrnative.SchnorrSecretKey
import com.horizen.secret.SchnorrSecret
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.{After, Test}

import java.io._
import java.util.Optional
import java.{lang, util}
import scala.collection.JavaConverters._
import scala.util.Random

class SigProofWithKeyRotationTest {
  private val classLoader: ClassLoader = getClass.getClassLoader
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

  private def buildSchnorrPrivateKey(index: Int): SchnorrSecretKey = {
    var bytes: Array[Byte] = null
    try {
      val resourceName = "schnorr_sk0"+ index + "_hex"
      val file = new FileReader(classLoader.getResource(resourceName).getFile)
      bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine())
    }
    catch {
      case e: Exception =>
        assertEquals(e.toString(), true, false)
    }

    SchnorrSecretKey.deserialize(bytes)
  }

  //Test will take around 2 minutes, enable for sanity checking of ThresholdSignatureCircuitWithKeyRotation
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

    val wb: util.List[WithdrawalRequestBox] = Seq(new WithdrawalRequestBox(new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte)), 2345), 42)).asJava

    // Try with no key updates and first epoch
    val messageToBeSignedForEpoch0 = sigCircuit.generateMessageToBeSigned(wb, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, util.Arrays.asList(genesisKeyRootHash))

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
    if (!CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateCoboundaryMarlinSnarkKeys(keyPairsLen, provingKeyPath, verificationKeyPath, CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION)) {
      fail("Error occurred during snark keys generation.")
    }

    println("Generating snark proof...")
    val emptyUpdateProofs = List.fill[Option[Array[Byte]]](keyPairsLen)(Option.empty[Array[Byte]])

    var schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      signerPublicKeysBytes.asScala,
      masterPublicKeysBytes.asScala,
      signerPublicKeysBytes.asScala,
      masterPublicKeysBytes.asScala,
      emptyUpdateProofs,
      emptyUpdateProofs,
      emptyUpdateProofs,
      emptyUpdateProofs
    )
    var actualKeys = getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes)

    var customFields = new util.ArrayList[Array[Byte]]()
    customFields.add(actualKeys.getUpdatedKeysRootHash(keyPairsLen).serializeFieldElement())

    var proofAndQuality: utils.Pair[Array[Byte], lang.Long] = sigCircuit.createProof(wb, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, customFields, signaturesForEpoch0, schnorrKeysSignaturesListBytes,
      threshold, Optional.empty(), 2, genesisKeyRootHash, provingKeyPath, true, true)


    var result = sigCircuit.verifyProof(wb, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, customFields,
      threshold.asInstanceOf[Long], Optional.empty(), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)

    // Try with no key updates and no first epoch
    val epochNumber10 = 10
    val messageToBeSignedForEpoch10 = sigCircuit.generateMessageToBeSigned(wb, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, util.Arrays.asList(genesisKeyRootHash))
    val signaturesForEpoch10 = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedForEpoch10)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    var prevCustomFields = new util.ArrayList[Array[Byte]]()
    prevCustomFields.add(actualKeys.getKeysRootHash(keyPairsLen).serializeFieldElement())

    var prevCertificate = WithdrawalEpochCertificate(
      Array[Byte](),
      keyPairsLen,
      sidechainId,
      epochNumber10 - 1,
      threshold.asInstanceOf[Long] - 1,
      Array[Byte](),
      prevCustomFields.asScala.map(el => FieldElementCertificateField(el)),
      Seq(),
      actualKeys.getKeysRootHash(keyPairsLen).serializeFieldElement(),
      btrFee,
      ftMinAmount,
      Seq(),
      Seq(),
      Seq()
    )
    println("Generating snark proof...")
    proofAndQuality = sigCircuit.createProof(wb, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, customFields, signaturesForEpoch10, schnorrKeysSignaturesListBytes,
      threshold, Optional.of(prevCertificate), 2, genesisKeyRootHash, provingKeyPath, true, true)

    result = sigCircuit.verifyProof(wb, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, customFields,
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
    for (i<-1 until signerPublicKeysBytes.size()) {
      updatedSignerPublicKeysBytes.add(signerPublicKeysBytes.get(i))
      updatedMasterPublicKeysBytes.add(masterPublicKeysBytes.get(i))
    }

    val oldSignerSignature = new SchnorrSecret(signerKeyPairs.head._1.serializeSecretKey(), signerKeyPairs.head._2.serializePublicKey()).sign(newKeyToSign).bytes()
    val olderMasterSignature = new SchnorrSecret(masterKeyPairs.head._1.serializeSecretKey(), masterKeyPairs.head._2.serializePublicKey()).sign(newKeyToSign).bytes()

    val signerSignatures = new util.ArrayList[Option[Array[Byte]]]()
    signerSignatures.add(Option.apply(oldSignerSignature))
    val masterSignatures = new util.ArrayList[Option[Array[Byte]]]()
    masterSignatures.add(Option.apply(olderMasterSignature))
    for(i <- 0 until keyPairsLen-1) {
      signerSignatures.add(Option.empty)
      masterSignatures.add(Option.empty)
    }

    // Try with no key updates and first epoch

    println("Generating snark proof...")

    schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      signerPublicKeysBytes.asScala,
      masterPublicKeysBytes.asScala,
      updatedSignerPublicKeysBytes.asScala,
      masterPublicKeysBytes.asScala,
      signerSignatures.asScala,
      masterSignatures.asScala,
      emptyUpdateProofs,
      emptyUpdateProofs
    )

    actualKeys = getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes)

    customFields = new util.ArrayList[Array[Byte]]()
    customFields.add(actualKeys.getUpdatedKeysRootHash(keyPairsLen).serializeFieldElement())

    val messageToBeSignedWithRotationEpoch0 = sigCircuit.generateMessageToBeSigned(wb, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, util.Arrays.asList(actualKeys.getUpdatedKeysRootHash(keyPairsLen).serializeFieldElement()))

    val signaturesWithRotationEpoch0: util.List[Optional[Array[Byte]]] = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedWithRotationEpoch0)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    proofAndQuality = sigCircuit.createProof(wb, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, customFields, signaturesWithRotationEpoch0, schnorrKeysSignaturesListBytes,
      threshold, Optional.empty(), 2, genesisKeyRootHash, provingKeyPath, true, true)


    result = sigCircuit.verifyProof(wb, sidechainId, epochNumber0, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, customFields,
      threshold.asInstanceOf[Long], Optional.empty(), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)

    // Try with master key updates and no first epoch

    println("Generating snark proof...")

    schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      signerPublicKeysBytes.asScala,
      masterPublicKeysBytes.asScala,
      signerPublicKeysBytes.asScala,
      updatedMasterPublicKeysBytes.asScala,
      emptyUpdateProofs,
      emptyUpdateProofs,
      signerSignatures.asScala,
      masterSignatures.asScala
    )

    actualKeys = getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes)
    customFields = new util.ArrayList[Array[Byte]]()
    customFields.add(actualKeys.getUpdatedKeysRootHash(keyPairsLen).serializeFieldElement())

    prevCustomFields = new util.ArrayList[Array[Byte]]()
    prevCustomFields.add(actualKeys.getKeysRootHash(keyPairsLen).serializeFieldElement())

    prevCertificate = WithdrawalEpochCertificate(
      Array[Byte](),
      keyPairsLen,
      sidechainId,
      epochNumber10 - 1,
      threshold.asInstanceOf[Long] - 1,
      Array[Byte](),
      prevCustomFields.asScala.map(el => FieldElementCertificateField(el)),
      Seq(),
      actualKeys.getKeysRootHash(keyPairsLen).serializeFieldElement(),
      btrFee,
      ftMinAmount,
      Seq(),
      Seq(),
      Seq()
    )

    val messageToBeSignedWithRotationEpoch10 = sigCircuit.generateMessageToBeSigned(wb, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, util.Arrays.asList(actualKeys.getUpdatedKeysRootHash(keyPairsLen).serializeFieldElement()))

    val signaturesWithRotationEpoch10: util.List[Optional[Array[Byte]]] = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedWithRotationEpoch10)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    proofAndQuality = sigCircuit.createProof(wb, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, customFields, signaturesWithRotationEpoch10, schnorrKeysSignaturesListBytes,
      threshold, Optional.of(prevCertificate), 2, genesisKeyRootHash, provingKeyPath, true, true)

    result = sigCircuit.verifyProof(wb, sidechainId, epochNumber10, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, customFields,
      threshold.asInstanceOf[Long], Optional.of(prevCertificate), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)
  }
}
