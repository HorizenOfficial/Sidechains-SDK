package com.horizen

import com.google.common.io.Files
import com.horizen.box.WithdrawalRequestBox
import com.horizen.box.data.WithdrawalRequestBoxData
import com.horizen.certificatesubmitter.dataproof.CertificateDataWithKeyRotation
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes
import com.horizen.cryptolibprovider.implementations.{SchnorrFunctionsImplZendoo, ThresholdSignatureCircuitImplZendoo, ThresholdSignatureCircuitWithKeyRotationImplZendoo}
import com.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider}
import com.horizen.fixtures.FieldElementFixture
import com.horizen.librustsidechains.FieldElement
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.schnorrnative.{SchnorrPublicKey, SchnorrSecretKey, SchnorrSignature, ValidatorKeysUpdatesList}
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.{After, Ignore, Test}

import java.io._
import java.util.{ArrayList, Optional}
import java.{lang, util}
import scala.collection.JavaConverters
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

    val epochNumber: Int = 0
    val btrFee: Long = 100
    val ftMinAmount: Long = 100
    val endCumulativeScTxCommTreeRoot = FieldElementFixture.generateFieldElement()
    val sidechainId = FieldElementFixture.generateFieldElement()
    val genesisKeyRootHash = CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.generateKeysRootHash(signerPublicKeysBytes, masterPublicKeysBytes)

    val wb: util.List[WithdrawalRequestBox] = Seq(new WithdrawalRequestBox(new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte)), 2345), 42)).asJava

    // Try with no key updates and first certificate
    val messageToBeSigned = sigCircuit.generateMessageToBeSigned(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, util.Arrays.asList(genesisKeyRootHash))

    val emptySigs = List.fill[Optional[Array[Byte]]](keyPairsLen - threshold)(Optional.empty[Array[Byte]]())
    val signatures: util.List[Optional[Array[Byte]]] = (signerKeyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSigned)}
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
    val actualKeys = new ValidatorKeysUpdatesList(
      signerKeyPairs.map(_._2).toList.asJava,
      masterKeyPairs.map(_._2).toList.asJava,
      signerKeyPairs.map(_._2).toList.asJava,
      masterKeyPairs.map(_._2).toList.asJava,
      List.fill[SchnorrSignature](keyPairsLen)(new SchnorrSignature()).asJava,
      List.fill[SchnorrSignature](keyPairsLen)(new SchnorrSignature()).asJava,
      List.fill[SchnorrSignature](keyPairsLen)(new SchnorrSignature()).asJava,
      List.fill[SchnorrSignature](keyPairsLen)(new SchnorrSignature()).asJava)

    val emptyUpdateProofs = List.fill[Option[Array[Byte]]](keyPairsLen)(Option.empty[Array[Byte]])

    val schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      signerPublicKeysBytes.asScala,
      masterPublicKeysBytes.asScala,
      signerPublicKeysBytes.asScala,
      masterPublicKeysBytes.asScala,
      emptyUpdateProofs,
      emptyUpdateProofs,
      emptyUpdateProofs,
      emptyUpdateProofs
    )

    val customFields = new util.ArrayList[Array[Byte]]()
    customFields.add(actualKeys.getUpdatedKeysRootHash(keyPairsLen).serializeFieldElement())

    val proofAndQuality: utils.Pair[Array[Byte], lang.Long] = sigCircuit.createProof(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, customFields, signatures, schnorrKeysSignaturesListBytes,
      threshold, Optional.empty(), 2, genesisKeyRootHash, provingKeyPath, true, true)


    val result = sigCircuit.verifyProof(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, customFields,
      threshold.asInstanceOf[Long], Optional.empty(), sysConstant, 2, proofAndQuality.getKey, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)
  }
}
