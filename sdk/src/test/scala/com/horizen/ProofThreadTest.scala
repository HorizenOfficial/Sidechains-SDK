package com.horizen

import java.io.{BufferedReader, File, FileReader}
import java.{lang, util}
import java.util.Optional

import com.horizen.box.WithdrawalRequestBox
import com.horizen.box.data.WithdrawalRequestBoxData
import com.horizen.cryptolibprovider.{CryptoLibProvider, SchnorrFunctionsImplZendoo}
import com.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import com.horizen.proposition.{MCPublicKeyHashProposition}
import com.horizen.schnorrnative.SchnorrSecretKey
import com.horizen.utils.{BytesUtils}
import com.horizen.params.{RegTestParams}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{Ignore, Test}

import scala.util.{Random}
import scala.collection.JavaConverters._

class ProofThreadTest {
  private val classLoader: ClassLoader = getClass.getClassLoader
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

  case class DataForProofGeneration(processedEpochNumber: Int,
                                    threshold: Int,
                                    withdrawalRequests: Seq[WithdrawalRequestBox],
                                    endWithdrawalEpochBlockHash: Array[Byte],
                                    prevEndWithdrawalEpochBlockHash: Array[Byte],
                                    publicKeysBytes: util.List[Array[Byte]],
                                    signatures:util.List[Optional[Array[Byte]]]
                                   )

  class MyThread(val dataForProofGeneration:DataForProofGeneration) extends Thread
  {
    var proofWithQuality:com.horizen.utils.Pair[Array[Byte], java.lang.Long] = null

    override def run()
    {
      proofWithQuality = generateProof(dataForProofGeneration)
      generateProof(dataForProofGeneration)
    }
  }

  def tryGenerateProof = {
    val dataForProofGeneration:DataForProofGeneration = buildDataForProofGeneration()

    var th = new MyThread(dataForProofGeneration)
    th.start()
    th.join()

    val params = new RegTestParams

    val certificateRequest: SendCertificateRequest = CertificateRequestCreator.create(
      dataForProofGeneration.processedEpochNumber,
      dataForProofGeneration.endWithdrawalEpochBlockHash,
      th.proofWithQuality.getKey,
      th.proofWithQuality.getValue,
      dataForProofGeneration.withdrawalRequests,
      params)
    true
  }

  private def buildDataForProofGeneration(): DataForProofGeneration = {
    val keyPairsLen = 7
    val threshold = 5 //hardcoded value

    val keyPairs = (0 until keyPairsLen).view.map(buildSchnorrPrivateKey).map(secret => (secret, secret.getPublicKey))
    val publicKeysBytes: util.List[Array[Byte]] = keyPairs.map(_._2.serializePublicKey()).toList.asJava
    val mcBlockHash = Array.fill(32)(Random.nextInt().toByte)
    val previousMcBlockHash = Array.fill(32)(Random.nextInt().toByte)

    val wb: Seq[WithdrawalRequestBox] = Seq(new WithdrawalRequestBox(new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte)), 2345), 42))

    val endEpochBlockHashLE = BytesUtils.reverseBytes(mcBlockHash)
    val previousEndEpochBlockHashLE = BytesUtils.reverseBytes(previousMcBlockHash)
    val message = CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned(wb.asJava, endEpochBlockHashLE, previousEndEpochBlockHashLE)
    val emptySigs = List.fill[Optional[Array[Byte]]](keyPairsLen - threshold)(Optional.empty[Array[Byte]]())

    val signatures: util.List[Optional[Array[Byte]]] = (keyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), message)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    DataForProofGeneration(1, threshold, wb, mcBlockHash, previousMcBlockHash, publicKeysBytes, signatures)
  }

  private def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    val provingKeyPath = new File(classLoader.getResource("sample_proving_key_7_keys_with_threshold_5").getFile).getAbsolutePath;
    CryptoLibProvider.sigProofThresholdCircuitFunctions.createProof(
      dataForProofGeneration.withdrawalRequests.asJava,
      BytesUtils.reverseBytes(dataForProofGeneration.endWithdrawalEpochBlockHash), // Pass block hash in LE endianness
      BytesUtils.reverseBytes(dataForProofGeneration.prevEndWithdrawalEpochBlockHash), // Pass block hash in LE endianness
      dataForProofGeneration.publicKeysBytes,
      dataForProofGeneration.signatures,
      dataForProofGeneration.threshold,
      provingKeyPath)
  }

  @Ignore
  @Test
  def simpleCheck(): Unit = {

    for (i <- 1 to 15) {
      println("Thread proof generation # " + i)
      tryGenerateProof
    }
  }
}