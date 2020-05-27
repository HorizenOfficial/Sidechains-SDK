package com.horizen

import java.io.{BufferedReader, File, FileReader}
import java.{lang, util}
import java.util.Optional

import com.horizen.box.WithdrawalRequestBox
import com.horizen.box.data.WithdrawalRequestBoxData
import com.horizen.cryptolibprovider.{SchnorrFunctionsImplZendoo, ThresholdSignatureCircuitImplZendoo}
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.schnorrnative.SchnorrSecretKey
import com.horizen.utils.BytesUtils
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

import scala.collection.JavaConverters._
import scala.util.Random

class SigProofTest {
  private val classLoader: ClassLoader = getClass.getClassLoader
  private val sigCircuit: ThresholdSignatureCircuitImplZendoo = new ThresholdSignatureCircuitImplZendoo()
  private val schnorrFunctions: SchnorrFunctionsImplZendoo = new SchnorrFunctionsImplZendoo()

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

  //Test will take around 2 minutes, enable for sanity checking of ThresholdSignatureCircuit
  @Test
  def simpleCheck(): Unit = {
    val keyPairsLen = 7
    val threshold = 5 //hardcoded value

    val keyPairs = (0 until keyPairsLen).view.map(buildSchnorrPrivateKey).map(secret => (secret, secret.getPublicKey))
    val publicKeysBytes: util.List[Array[Byte]] = keyPairs.map(_._2.serializePublicKey()).toList.asJava
    val provingKeyPath = new File(classLoader.getResource("sample_proving_key_7_keys_with_threshold_5").getFile).getAbsolutePath;
    val verificationKeyPath = new File(classLoader.getResource("sample_vk_7_keys_with_threshold_5").getFile).getAbsolutePath;

    val sysConstant = sigCircuit.generateSysDataConstant(publicKeysBytes, threshold)

    val mcBlockHash = Array.fill(32)(Random.nextInt().toByte)
    val previousMcBlockHash = Array.fill(32)(Random.nextInt().toByte)

    val wb: util.List[WithdrawalRequestBox] = Seq(new WithdrawalRequestBox(new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte)), 2345), 42)).asJava

    val messageToBeSigned = sigCircuit.generateMessageToBeSigned(wb, mcBlockHash, previousMcBlockHash)

    val emptySigs = List.fill[Optional[Array[Byte]]](keyPairsLen - threshold)(Optional.empty[Array[Byte]]())
    val signatures: util.List[Optional[Array[Byte]]] = (keyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), messageToBeSigned)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    val proofAndQuality: utils.Pair[Array[Byte], lang.Long] = sigCircuit.createProof(wb, mcBlockHash, previousMcBlockHash, publicKeysBytes, signatures, threshold, provingKeyPath)

    val result = sigCircuit.verifyProof(wb, mcBlockHash, previousMcBlockHash, proofAndQuality.getValue, proofAndQuality.getKey, sysConstant, verificationKeyPath)

    assertTrue("Proof verification expected to be successfully", result)
  }

}
