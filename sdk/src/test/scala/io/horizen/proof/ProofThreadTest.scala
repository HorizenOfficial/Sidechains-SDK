package io.horizen.proof

import com.google.common.io.Files
import com.horizen.certnative.BackwardTransfer
import io.horizen.cryptolibprovider.implementations.SchnorrFunctionsImplZendoo
import io.horizen.cryptolibprovider.{CommonCircuit, CryptoLibProvider}
import io.horizen.fixtures.{FieldElementFixture, SecretFixture}
import io.horizen.mainchain.api.{CertificateRequestCreator, SendCertificateRequest}
import io.horizen.params.{NetworkParams, RegTestParams}
import io.horizen.proposition.MCPublicKeyHashProposition
import io.horizen.utxo.box.WithdrawalRequestBox
import io.horizen.utxo.box.data.WithdrawalRequestBoxData
import org.junit.Assert.fail
import org.junit.{Ignore, Test}

import java.io.File
import java.util
import java.util.Optional
import scala.collection.JavaConverters._
import scala.util.Random

/**
 * This test was create for profiling of JVM memory use during proof generation.
 */

class ProofThreadTest extends SecretFixture {
  private val schnorrFunctions: SchnorrFunctionsImplZendoo = new SchnorrFunctionsImplZendoo()
  var proofWithQuality:io.horizen.utils.Pair[Array[Byte], java.lang.Long] = null

  private val tmpDir: File = Files.createTempDir
  private val provingKeyPath: String = tmpDir.getAbsolutePath + "/snark_pk"
  private val verificationKeyPath: String = tmpDir.getAbsolutePath + "/snark_vk"
  val keyPairsLen = 7

  case class DataForProofGeneration(sidechainId: Array[Byte],
                                    processedEpochNumber: Int,
                                    threshold: Int,
                                    withdrawalRequests: Seq[WithdrawalRequestBox],
                                    endCumulativeEpochBlockHash: Array[Byte],
                                    publicKeysBytes: util.List[Array[Byte]],
                                    signatures:util.List[Optional[Array[Byte]]],
                                    merkelTreeRoot: Array[Byte]
                                   )

  class MyThread(val dataForProofGeneration:DataForProofGeneration) extends Thread
  {
    override def run(): Unit = {
      proofWithQuality = generateProof(dataForProofGeneration)
    }
  }

  def tryGenerateProof: Boolean = {
    val params = new RegTestParams
    val dataForProofGeneration:DataForProofGeneration = buildDataForProofGeneration(params)

    val th = new MyThread(dataForProofGeneration)
    th.start()
    th.join()


    val certificateRequest: SendCertificateRequest = CertificateRequestCreator.create(
      params.sidechainId,
      dataForProofGeneration.processedEpochNumber,
      dataForProofGeneration.endCumulativeEpochBlockHash,
      proofWithQuality.getKey,
      proofWithQuality.getValue,
      dataForProofGeneration.withdrawalRequests.map(box => new BackwardTransfer(box.proposition.bytes, box.value)),
      0,
      0,
      Seq(Array(0)),
      None,
      params)
    true
  }

  private def buildDataForProofGeneration(params: NetworkParams): DataForProofGeneration = {
    val keyPairsLen = 7
    val threshold = 5 //hardcoded value

    val sidechainId = params.sidechainId
    val epochNumber = 1
    val keyPairs = (0 until keyPairsLen).view.map(buildSchnorrPrivateKey).map(secret => (secret, secret.getPublicKey))
    val publicKeysBytes: util.List[Array[Byte]] = keyPairs.map(_._2.serializePublicKey()).toList.asJava
    val endCummulativeTransactionCommTreeHash = FieldElementFixture.generateFieldElement()
    val merkelTreeRoot = FieldElementFixture.generateFieldElement()

    val wb: Seq[WithdrawalRequestBox] = Seq(new WithdrawalRequestBox(new WithdrawalRequestBoxData(new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte)), 2345), 42))

    val message = CryptoLibProvider.sigProofThresholdCircuitFunctions.generateMessageToBeSigned(
      wb.map(box => new BackwardTransfer(box.proposition.bytes, box.value)).asJava,
      sidechainId, epochNumber, endCummulativeTransactionCommTreeHash, 0, 0, Optional.of(merkelTreeRoot))
    val emptySigs = List.fill[Optional[Array[Byte]]](keyPairsLen - threshold)(Optional.empty[Array[Byte]]())

    val signatures: util.List[Optional[Array[Byte]]] = (keyPairs
      .map{case (secret, public) => schnorrFunctions.sign(secret.serializeSecretKey(), public.serializePublicKey(), message)}
      .map(b => Optional.of(b))
      .take(threshold)
      .toList ++ emptySigs)
      .asJava

    DataForProofGeneration(sidechainId, epochNumber, threshold, wb, endCummulativeTransactionCommTreeHash, publicKeysBytes, signatures, merkelTreeRoot)
  }

  private def generateProof(dataForProofGeneration: DataForProofGeneration): io.horizen.utils.Pair[Array[Byte], java.lang.Long] = {
    CryptoLibProvider.sigProofThresholdCircuitFunctions.createProof(dataForProofGeneration.withdrawalRequests.map(box => new BackwardTransfer(box.proposition.bytes, box.value)).asJava, dataForProofGeneration.sidechainId, dataForProofGeneration.processedEpochNumber, dataForProofGeneration.endCumulativeEpochBlockHash, 0, 0, Optional.of(dataForProofGeneration.merkelTreeRoot), dataForProofGeneration.signatures, dataForProofGeneration.publicKeysBytes, dataForProofGeneration.threshold, provingKeyPath, true, true)
  }

  @Ignore
  @Test
  def simpleCheck(): Unit = {
    // Setup proving system keys
    println(s"Generating Marlin dlog key.")
    if (!CryptoLibProvider.commonCircuitFunctions.generateCoboundaryMarlinDLogKeys()) {
      fail("Error occurred during dlog key generation.")
    }

    println(s"Generating Marlin snark keys. Path: pk=$provingKeyPath, vk=$verificationKeyPath")
    if (!CryptoLibProvider.sigProofThresholdCircuitFunctions.generateCoboundaryMarlinSnarkKeys(keyPairsLen, provingKeyPath, verificationKeyPath, CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_ENABLED_CSW)) {
      fail("Error occurred during snark keys generation.")
    }

    for (i <- 1 to 15) {
      println("Thread proof generation # " + i)
      tryGenerateProof
    }
  }
}