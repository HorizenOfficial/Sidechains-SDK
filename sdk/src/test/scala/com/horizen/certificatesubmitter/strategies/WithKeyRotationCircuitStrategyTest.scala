package com.horizen.certificatesubmitter.strategies

import akka.util.Timeout
import com.horizen._
import com.horizen.block.{SidechainBlockHeader, SidechainCreationVersions, WithdrawalEpochCertificate}
import com.horizen.certificatesubmitter.AbstractCertificateSubmitter.{CertificateSignatureInfo, SignaturesStatus}
import com.horizen.certificatesubmitter.dataproof.{CertificateData, CertificateDataWithKeyRotation}
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, SchnorrKeysSignatures}
import com.horizen.certificatesubmitter.strategies.WithoutKeyRotationCircuitStrategyTest.{master, signing}
import com.horizen.certnative.BackwardTransfer
import com.horizen.chain.MainchainHeaderInfo
import com.horizen.cryptolibprovider.ThresholdSignatureCircuitWithKeyRotation
import com.horizen.fork.{ForkManagerUtil, SimpleForkConfigurator}
import com.horizen.librustsidechains.FieldElement
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.params.RegTestParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.schnorrnative.SchnorrKeyPair
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import com.horizen.utxo.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import com.horizen.utxo.block.SidechainBlock
import com.horizen.utxo.box.WithdrawalRequestBox
import org.junit.{Before, Test}
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import sparkz.util.ModifierId
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.settings.{RESTApiSettings, SparkzSettings}

import java.lang
import java.nio.charset.StandardCharsets
import java.util.Optional
import scala.collection.mutable.ArrayBuffer
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.concurrent.duration._
import scala.language.postfixOps

class WithKeyRotationCircuitStrategyTest extends JUnitSuite with MockitoSugar {

  implicit val timeout: Timeout = 100 milliseconds
  var params: RegTestParams = _

  @Before
  def init(): Unit = {
    val keyGenerator = SchnorrKeyGenerator.getInstance()
    val schnorrSecrets: Seq[SchnorrSecret] = Seq(
      keyGenerator.generateSecret("seed1".getBytes(StandardCharsets.UTF_8)),
      keyGenerator.generateSecret("seed2".getBytes(StandardCharsets.UTF_8)),
      keyGenerator.generateSecret("seed3".getBytes(StandardCharsets.UTF_8))
    )
    val signersThreshold = 2
    params = RegTestParams(
      sidechainCreationVersion = SidechainCreationVersions.SidechainCreationVersion2,
      signersPublicKeys = schnorrSecrets.map(_.publicImage()),
      mastersPublicKeys = schnorrSecrets.map(_.publicImage()),
      signersThreshold = signersThreshold
    )

    val forkManagerUtil = new ForkManagerUtil()
    forkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")
  }

  @Test
  def buildCertificateDataTest(): Unit = {
    val certificateSignatureInfo = CertificateSignatureInfo(pubKeyIndex = 3,
      signature = new SchnorrProof(signing.signMessage(signing.getPublicKey.getHash).serializeSignature()))
    val signaturesStatus = SignaturesStatus(
      referencedEpoch = WithKeyRotationCircuitStrategyTest.epochNumber,
      messageToSign = Array(135.toByte),
      knownSigs = ArrayBuffer(certificateSignatureInfo)
    )

    val mockedCryptolibCircuit = mock[ThresholdSignatureCircuitWithKeyRotation]
    val keyRotationStrategy: CircuitStrategy[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainHistory, SidechainState, CertificateDataWithKeyRotation] = new WithKeyRotationCircuitStrategy(settings(), params, mockedCryptolibCircuit)
    val certificateDataWithKeyRotation: CertificateDataWithKeyRotation =
      keyRotationStrategy.buildCertificateData(sidechainNodeView().history, sidechainNodeView().state, signaturesStatus)

    assert(certificateDataWithKeyRotation.btrFee == 0)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.updatedSigningKeysSkSignatures.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.updatedMasterKeysMkSignatures.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.updatedMasterKeysSkSignatures.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.updatedSigningKeysMkSignatures.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.schnorrMasters.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.newSchnorrMasters.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.schnorrSigners.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeysSignatures.newSchnorrSigners.length == 1)
    assert(certificateDataWithKeyRotation.schnorrKeyPairs.length == 1)
    assert(certificateDataWithKeyRotation.endEpochCumCommTreeHash.length == 32)
    assert(certificateDataWithKeyRotation.sidechainId.length == 32)
    assert(certificateDataWithKeyRotation.referencedEpochNumber == WithKeyRotationCircuitStrategyTest.epochNumber)
    assert(certificateDataWithKeyRotation.previousCertificateOption == null)
    assert(certificateDataWithKeyRotation.ftMinAmount == 54)
  }

  @Test
  def generateProofTest(): Unit = {
    val info = for (_ <- 0 until WithKeyRotationCircuitStrategyTest.keyCount) yield {
      val signerKeyPair: SchnorrKeyPair = SchnorrKeyPair.generate
      val masterKeyPair: SchnorrKeyPair = SchnorrKeyPair.generate

      val updatedSigningKeysSkSignature = signerKeyPair.signMessage(signerKeyPair.getPublicKey.getHash)
      val updatedSigningKeysMkSignature = masterKeyPair.signMessage(signerKeyPair.getPublicKey.getHash)
      val updatedMasterKeysSkSignature = signerKeyPair.signMessage(masterKeyPair.getPublicKey.getHash)
      val updatedMasterKeysMkSignature = masterKeyPair.signMessage(masterKeyPair.getPublicKey.getHash)
      (signerKeyPair.getPublicKey, masterKeyPair.getPublicKey, updatedSigningKeysSkSignature,
        updatedSigningKeysMkSignature, updatedMasterKeysSkSignature, updatedMasterKeysMkSignature)
    }

    val signingKey = info.map(_._1.serializePublicKey()).map(b => new SchnorrProposition(b))
    val masterKey = info.map(_._2.serializePublicKey()).map(b => new SchnorrProposition(b))
    val schnorrKeysSignatures = SchnorrKeysSignatures(
      signingKey,
      masterKey,
      signingKey,
      masterKey,
      info.map(x => Option.apply(new SchnorrProof(x._3.serializeSignature()))),
      info.map(x => Option.apply(new SchnorrProof(x._4.serializeSignature()))),
      info.map(x => Option.apply(new SchnorrProof(x._5.serializeSignature()))),
      info.map(x => Option.apply(new SchnorrProof(x._6.serializeSignature())))
    )

    val schnorrPropositionsAndSchnorrProofs: Seq[(SchnorrProposition, Option[SchnorrProof])] =
      for (i <- 0 until WithKeyRotationCircuitStrategyTest.keyCount) yield {
        (schnorrKeysSignatures.newSchnorrSigners(i),
          Some(schnorrKeysSignatures.updatedSigningKeysSkSignatures(i).get))
      }


    val certificateData = CertificateDataWithKeyRotation(
      referencedEpochNumber = WithKeyRotationCircuitStrategyTest.epochNumber,
      sidechainId = FieldElement.createRandom.serializeFieldElement(),
      backwardTransfers = Seq[BackwardTransfer](),
      endEpochCumCommTreeHash = FieldElement.createRandom.serializeFieldElement(),
      btrFee = WithKeyRotationCircuitStrategyTest.btrFee,
      ftMinAmount = WithKeyRotationCircuitStrategyTest.ftMinAmount,
      schnorrKeyPairs = schnorrPropositionsAndSchnorrProofs,
      schnorrKeysSignatures,
      previousCertificateOption = Option.empty[WithdrawalEpochCertificate],
      genesisKeysRootHash = FieldElement.createRandom.serializeFieldElement()
    )

    val key = new Array(4.toByte)
    val mockedCryptolibCircuit = mock[ThresholdSignatureCircuitWithKeyRotation]
    Mockito.when(mockedCryptolibCircuit.createProof(ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any())) thenAnswer (answer => {
      assertResult(32)(answer.getArgument(1).asInstanceOf[Array[Byte]].length)
      assertResult(10)(answer.getArgument(2).asInstanceOf[Integer])
      assertResult(100L)(answer.getArgument(4).asInstanceOf[Long])
      assertResult(200L)(answer.getArgument(5).asInstanceOf[Long])
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].updatedSigningKeysSkSignatures.length)
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].updatedSigningKeysMkSignatures.length)
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].updatedMasterKeysSkSignatures.length)
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].updatedMasterKeysMkSignatures.length)
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].schnorrSigners.length)
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].schnorrMasters.length)
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].newSchnorrSigners.length)
      assertResult(4)(answer.getArgument(7).asInstanceOf[SchnorrKeysSignatures].newSchnorrMasters.length)
      assertResult(2L)(answer.getArgument(8).asInstanceOf[Long])
      assertResult(Optional.empty())(answer.getArgument(9).asInstanceOf[Optional[Integer]])
      assertResult(2)(answer.getArgument(10).asInstanceOf[Integer])
      assertResult(32)(answer.getArgument(11).asInstanceOf[Array[Byte]].length)
      assertResult("filePath")(answer.getArgument(12).asInstanceOf[String])
      assertResult(true)(answer.getArgument(13).asInstanceOf[Boolean])
      new com.horizen.utils.Pair(key, 429L)
    })
    val keyRotationStrategy: CircuitStrategy[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainHistory, SidechainState, CertificateDataWithKeyRotation] = new WithKeyRotationCircuitStrategy(settings(), params, mockedCryptolibCircuit)
    val pair: utils.Pair[Array[Byte], lang.Long] = keyRotationStrategy.generateProof(certificateData, provingFileAbsolutePath = "filePath")
    assert(pair.getValue == 429L)
    info.foreach(element => {
      element._3.freeSignature()
      element._4.freeSignature()
      element._5.freeSignature()
      element._6.freeSignature()
    })
  }

  @Test
  def getMessageToSignTest(): Unit = {
    val mockedCryptolibCircuit = mock[ThresholdSignatureCircuitWithKeyRotation]
    Mockito.when(mockedCryptolibCircuit.generateMessageToBeSigned(ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
      ArgumentMatchers.any())) thenAnswer (answer => {
      assertResult(32)(answer.getArgument(1).asInstanceOf[Array[Byte]].length)
      assertResult(10)(answer.getArgument(2).asInstanceOf[Integer])
      assertResult(32)(answer.getArgument(3).asInstanceOf[Array[Byte]].length)
      assertResult(0L)(answer.getArgument(4).asInstanceOf[Long])
      assertResult(54L)(answer.getArgument(5).asInstanceOf[Long])
      assertResult(32)(answer.getArgument(6).asInstanceOf[Array[Byte]].length)
      new com.horizen.utils.Pair(Array(73.toByte), 425L)
    })
    val keyRotationStrategy: CircuitStrategy[SidechainTypes#SCBT, SidechainBlockHeader, SidechainBlock, SidechainHistory, SidechainState, CertificateDataWithKeyRotation] = new WithKeyRotationCircuitStrategy(settings(), params, mockedCryptolibCircuit)
    keyRotationStrategy.getMessageToSign(sidechainNodeView().history, sidechainNodeView().state, WithKeyRotationCircuitStrategyTest.epochNumber)
  }

  private def settings(): SidechainSettings = {
    val signerIsEnabled = true
    val submitterIsEnabled = true
    val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
    when(mockedRESTSettings.timeout).thenReturn(timeout.duration)

    val mockedSidechainSettings: SidechainSettings = mock[SidechainSettings]
    when(mockedSidechainSettings.sparkzSettings).thenAnswer(_ => {
      val mockedSparkzSettings: SparkzSettings = mock[SparkzSettings]
      when(mockedSparkzSettings.restApi).thenAnswer(_ => mockedRESTSettings)
      mockedSparkzSettings
    })
    when(mockedSidechainSettings.withdrawalEpochCertificateSettings).thenAnswer(_ => {
      val mockedWithdrawalEpochCertificateSettings: WithdrawalEpochCertificateSettings = mock[WithdrawalEpochCertificateSettings]
      when(mockedWithdrawalEpochCertificateSettings.submitterIsEnabled).thenReturn(submitterIsEnabled)
      when(mockedWithdrawalEpochCertificateSettings.certificateSigningIsEnabled).thenReturn(signerIsEnabled)
      mockedWithdrawalEpochCertificateSettings
    })
    mockedSidechainSettings
  }

  private def sidechainNodeView(): CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool] = {
    val sidechainState = mock[SidechainState]
    val history = mock[SidechainHistory]
    when(history.mainchainHeaderInfoByHash(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => {
      val info: MainchainHeaderInfo = mock[MainchainHeaderInfo]
      when(info.cumulativeCommTreeHash).thenReturn(new Array[Byte](32))
      when(info.sidechainBlockId).thenReturn(ModifierId @@ "some_block_id")
      Some(info)
    })
    when(sidechainState.withdrawalRequests(ArgumentMatchers.anyInt())) thenAnswer (_ => Seq[WithdrawalRequestBox]())
    val certifiersKeys = CertifiersKeys(Vector(new SchnorrProposition(signing.getPublicKey.serializePublicKey())), Vector(new SchnorrProposition(master.getPublicKey.serializePublicKey)))
    when(sidechainState.certifiersKeys(ArgumentMatchers.anyInt())).thenAnswer(_ => Some(certifiersKeys))
    val mainchainBlockReferenceInfo = mock[MainchainBlockReferenceInfo]
    when(mainchainBlockReferenceInfo.getMainchainHeaderHash).thenAnswer(_ => Array(13.toByte))
    when(history.getMainchainBlockReferenceInfoByMainchainBlockHeight(ArgumentMatchers.anyInt())).thenAnswer(_ => Some(mainchainBlockReferenceInfo).asJava)

    CurrentView(history, sidechainState, mock[SidechainWallet], mock[SidechainMemoryPool])
  }
}

object WithKeyRotationCircuitStrategyTest {
  private val epochNumber = 10
  private val btrFee = 100L
  private val ftMinAmount = 200L
  val DLOG_KEYS_SIZE: Int = 1 << 18
  val CERT_SEGMENT_SIZE: Int = 1 << 15
  val CSW_SEGMENT_SIZE: Int = 1 << 18
  private val keyCount = 4
}
