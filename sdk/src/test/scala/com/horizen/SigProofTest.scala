package com.horizen

import com.google.common.io.Files
import com.horizen.cryptolibprovider.implementations.{SchnorrFunctionsImplZendoo, ThresholdSignatureCircuitImplZendoo}
import com.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider}
import com.horizen.certnative.BackwardTransfer
import com.horizen.fixtures.{FieldElementFixture, SecretFixture}
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.schnorrnative.{SchnorrKeyPair, SchnorrSecretKey}
import com.horizen.secret.SchnorrKeyGenerator
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue, fail}
import org.junit.{After, Ignore, Test}

import java.io._
import java.math.BigInteger
import java.util.Optional
import java.{lang, util}
import scala.collection.JavaConverters._
import scala.util.Random

class SigProofTest extends SecretFixture {
  private val classLoader: ClassLoader = getClass.getClassLoader
  private val sigCircuit: ThresholdSignatureCircuitImplZendoo = new ThresholdSignatureCircuitImplZendoo()
  private val schnorrFunctions: SchnorrFunctionsImplZendoo = new SchnorrFunctionsImplZendoo()

  private val tmpDir: File = Files.createTempDir
  private val provingKeyPath: String = tmpDir.getAbsolutePath + "/snark_pk"
  private val verificationKeyPath: String = tmpDir.getAbsolutePath + "/snark_vk"

  @After
  def cleanup(): Unit = {
    new File(provingKeyPath).delete()
    new File(verificationKeyPath).delete()
    tmpDir.delete()
  }

  //Test will take around 2 minutes, enable for sanity checking of ThresholdSignatureCircuit
  @Ignore
  @Test
  def simpleCheck(): Unit = {
    val keyPairsLen = 9
    val threshold = 6 //hardcoded value

    val keyPairs = (0 until keyPairsLen).view.map(buildSchnorrPrivateKey).map(secret => (secret, secret.getPublicKey))
    val publicKeysBytes: util.List[Array[Byte]] = keyPairs.map(_._2.serializePublicKey()).toList.asJava

    val sysConstant = sigCircuit.generateSysDataConstant(publicKeysBytes, threshold)

    val epochNumber: Int = 10
    val btrFee: Long = 100
    val ftMinAmount: Long = 100
    val endCumulativeScTxCommTreeRoot = FieldElementFixture.generateFieldElement()
    val sidechainId = FieldElementFixture.generateFieldElement()
    val utxoMerkleTreeRoot = Optional.of(FieldElementFixture.generateFieldElement())

    val wb: util.List[BackwardTransfer] = Seq(new BackwardTransfer((new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))).bytes(), 2345)).asJava

    println(s"withdrawalRequests=${wb.asScala.map( wr => s"[amount: ${wr.getAmount}, publicKeyHash: ${BytesUtils.toHexString(wr.getPublicKeyHash)}]" ).mkString("{",",", "}")}, ")
    val messageToBeSigned = sigCircuit.generateMessageToBeSigned(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, utxoMerkleTreeRoot)

    val emptySigs = List.fill[Optional[Array[Byte]]](keyPairsLen - threshold)(Optional.empty[Array[Byte]]())
    val signatures: util.List[Optional[Array[Byte]]] = (keyPairs
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
    if (!CryptoLibProvider.sigProofThresholdCircuitFunctions.generateCoboundaryMarlinSnarkKeys(keyPairsLen, provingKeyPath, verificationKeyPath, CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW)) {
      fail("Error occurred during snark keys generation.")
    }

    println("Generating snark proof...")
    val proofAndQuality: utils.Pair[Array[Byte], lang.Long] = sigCircuit.createProof(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, utxoMerkleTreeRoot, signatures, publicKeysBytes, threshold, provingKeyPath, true, true)

    val result = sigCircuit.verifyProof(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, utxoMerkleTreeRoot, sysConstant,
      proofAndQuality.getValue, proofAndQuality.getKey, true, verificationKeyPath, true)

    assertTrue("Proof verification expected to be successfully", result)

    println("Testing without utxoMerkleTreeRoot (as with CSW disabled)...")
    val utxoMerkleTreeRootCSWDisabled = Optional.empty[Array[Byte]]()

    val messageToBeSignedCSWDisabled = sigCircuit.generateMessageToBeSigned(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, utxoMerkleTreeRootCSWDisabled)

    val signaturesCSWDisabled: util.List[Optional[Array[Byte]]] = (keyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSignedCSWDisabled)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    println(s"Generating Marlin snark keys. Path: pk=$provingKeyPath, vk=$verificationKeyPath")
    if (!CryptoLibProvider.sigProofThresholdCircuitFunctions.generateCoboundaryMarlinSnarkKeys(keyPairsLen, provingKeyPath, verificationKeyPath, CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_NO_KEY_ROTATION)) {
      fail("Error occurred during snark keys generation.")
    }

    println("Generating snark proof...")
    val proofAndQualityCSWDisabled: utils.Pair[Array[Byte], lang.Long] = sigCircuit.createProof(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot,
      btrFee, ftMinAmount, utxoMerkleTreeRootCSWDisabled, signaturesCSWDisabled, publicKeysBytes, threshold, provingKeyPath, true, true)

    val resultCSWDisabled = sigCircuit.verifyProof(wb, sidechainId, epochNumber, endCumulativeScTxCommTreeRoot, btrFee, ftMinAmount, utxoMerkleTreeRootCSWDisabled, sysConstant,
      proofAndQualityCSWDisabled.getValue, proofAndQualityCSWDisabled.getKey, true, verificationKeyPath, true)

    assertTrue("Proof verification failed - CSW disabled", resultCSWDisabled)

  }
}
