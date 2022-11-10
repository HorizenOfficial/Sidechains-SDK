package com.horizen.certificatesubmitter.strategies

import akka.util.Timeout
import com.horizen._
import com.horizen.block.{SidechainCreationVersions, WithdrawalEpochCertificate}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.{CertificateSignatureInfo, SignaturesStatus}
import com.horizen.certificatesubmitter.dataproof.CertificateDataWithKeyRotation
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, SchnorrKeysSignaturesListBytes}
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
import org.junit.{Before, Test}
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito.MockitoSugar
import scorex.util.ModifierId
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.settings.{RESTApiSettings, SparkzSettings}

import scala.collection.mutable.ArrayBuffer
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

class WithKeyRotationStrategyTest extends JUnitSuite with MockitoSugar {

  private var keyRotationStrategy: KeyRotationStrategy[CertificateDataWithKeyRotation] = _
  implicit val timeout: Timeout = 100 milliseconds

  @Before
  def init(): Unit = {
    val mockedSettings: SidechainSettings = getMockedSettings(timeout.duration, submitterIsEnabled = true, signerIsEnabled = true)

    val keyGenerator = SchnorrKeyGenerator.getInstance()
    val schnorrSecrets: Seq[SchnorrSecret] = Seq(
      keyGenerator.generateSecret("seed1".getBytes()),
      keyGenerator.generateSecret("seed2".getBytes()),
      keyGenerator.generateSecret("seed3".getBytes())
    )
    val signersThreshold = 2
    val params: RegTestParams = RegTestParams(
      sidechainCreationVersion = SidechainCreationVersions.SidechainCreationVersion2,
      signersPublicKeys = schnorrSecrets.map(_.publicImage()),
      mastersPublicKeys = schnorrSecrets.map(_.publicImage()),
      signersThreshold = signersThreshold
    )

    val mockedCryptolibCircuit = mock[ThresholdSignatureCircuitWithKeyRotation]
    keyRotationStrategy = new WithKeyRotationStrategy(mockedSettings, params, mockedCryptolibCircuit)

    val forkManagerUtil = new ForkManagerUtil()
    forkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")
  }

  @Test
  def buildCertificateDataTest(): Unit = {
    type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
    val sidechainState = mock[SidechainState]
    when(sidechainState.withdrawalRequests(_)) thenAnswer (_ => Seq[WithdrawalRequestBox]())
    val certifiersKeys = CertifiersKeys(getSchnorrPropositions, getSchnorrPropositions)
    when(sidechainState.certifiersKeys(ArgumentMatchers.anyInt())).thenAnswer(_ => Some(certifiersKeys))

    val history = mock[SidechainHistory]
    val mainchainBlockReferenceInfo = mock[MainchainBlockReferenceInfo]
    when(mainchainBlockReferenceInfo.getMainchainHeaderHash).thenAnswer(_ => Array(13.toByte))
    when(history.getMainchainBlockReferenceInfoByMainchainBlockHeight(ArgumentMatchers.anyInt())).thenAnswer(_ => Some(mainchainBlockReferenceInfo).asJava)
    when(history.mainchainHeaderInfoByHash(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => {
      val info: MainchainHeaderInfo = mock[MainchainHeaderInfo]
      when(info.cumulativeCommTreeHash).thenReturn(new Array[Byte](32))
      when(info.sidechainBlockId).thenReturn(ModifierId @@ "some_block_id")
      Some(info)
    })

    val sidechainNodeView: View = CurrentView(history, sidechainState, mock[SidechainWallet], mock[SidechainMemoryPool])
    val certificateSignatureInfo = CertificateSignatureInfo(pubKeyIndex = 3, signature = getSchnorrProofs.head)
    val signaturesStatus = SignaturesStatus(
      referencedEpoch = WithKeyRotationStrategyTest.epochNumber,
      messageToSign = Array(135.toByte),
      knownSigs = ArrayBuffer(certificateSignatureInfo)
    )
    val certificateDataWithKeyRotation: CertificateDataWithKeyRotation =
      keyRotationStrategy.buildCertificateData(sidechainNodeView, signaturesStatus)
    assert(certificateDataWithKeyRotation.btrFee == 0)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.updatedSigningKeysSkSignatures.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.updatedMasterKeysMkSignatures.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.updatedMasterKeysSkSignatures.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.updatedSigningKeysMkSignatures.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.schnorrMastersPublicKeysBytesList.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.newSchnorrMastersPublicKeysBytesList.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.schnorrSignersPublicKeysBytesList.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeysSignaturesListBytes.newSchnorrSignersPublicKeysBytesList.length == 7)
    assert(certificateDataWithKeyRotation.schnorrKeyPairs.length == 3)
    assert(certificateDataWithKeyRotation.endEpochCumCommTreeHash.length == 32)
    assert(certificateDataWithKeyRotation.sidechainId.length == 32)
    assert(certificateDataWithKeyRotation.referencedEpochNumber == WithKeyRotationStrategyTest.epochNumber)
    assert(certificateDataWithKeyRotation.previousCertificateOption == null)
    assert(certificateDataWithKeyRotation.ftMinAmount == 54)
    assert(certificateDataWithKeyRotation.genesisKeysRootHash.length == 32)
  }

  @Test
  def generateProofTest(): Unit = {
    val info = for (_ <- 0 until WithKeyRotationStrategyTest.keyCount) yield {
      val signerKeyPair: SchnorrKeyPair = SchnorrKeyPair.generate
      val masterKeyPair: SchnorrKeyPair = SchnorrKeyPair.generate

      val updatedSigningKeysSkSignature = signerKeyPair.signMessage(signerKeyPair.getPublicKey.getHash)
      val updatedSigningKeysMkSignature = masterKeyPair.signMessage(signerKeyPair.getPublicKey.getHash)
      val updatedMasterKeysSkSignature = signerKeyPair.signMessage(masterKeyPair.getPublicKey.getHash)
      val updatedMasterKeysMkSignature = masterKeyPair.signMessage(masterKeyPair.getPublicKey.getHash)
      (signerKeyPair.getPublicKey, masterKeyPair.getPublicKey, updatedSigningKeysSkSignature,
        updatedSigningKeysMkSignature, updatedMasterKeysSkSignature, updatedMasterKeysMkSignature)
    }

    val schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      info.map(_._1.serializePublicKey()),
      info.map(_._2.serializePublicKey()),
      info.map(_._1.serializePublicKey()),
      info.map(_._2.serializePublicKey()),
      info.map(x => Option.apply(x._3.serializeSignature())),
      info.map(x => Option.apply(x._4.serializeSignature())),
      info.map(x => Option.apply(x._5.serializeSignature())),
      info.map(x => Option.apply(x._6.serializeSignature())),
    )

    val schnorrPropositionsAndSchnorrProofs: Seq[(SchnorrProposition, Option[SchnorrProof])] =
      for (i <- 0 until WithKeyRotationStrategyTest.keyCount) yield {
        (new SchnorrProposition(schnorrKeysSignaturesListBytes.newSchnorrSignersPublicKeysBytesList(i)),
          Some(new SchnorrProof(schnorrKeysSignaturesListBytes.updatedSigningKeysSkSignatures(i).get)))
      }


    val certificateData = CertificateDataWithKeyRotation(
      referencedEpochNumber = WithKeyRotationStrategyTest.epochNumber,
      sidechainId = FieldElement.createRandom.serializeFieldElement(),
      withdrawalRequests = Seq[WithdrawalRequestBox](),
      endEpochCumCommTreeHash = FieldElement.createRandom.serializeFieldElement(),
      btrFee = WithKeyRotationStrategyTest.btrFee,
      ftMinAmount = WithKeyRotationStrategyTest.ftMinAmount,
      schnorrKeyPairs = schnorrPropositionsAndSchnorrProofs,
      schnorrKeysSignaturesListBytes,
      previousCertificateOption = Option.empty[WithdrawalEpochCertificate],
      genesisKeysRootHash = FieldElement.createRandom.serializeFieldElement()
    )

    Mockito.when(keyRotationStrategy.generateProof(certificateData, provingFileAbsolutePath = "filePath")) thenAnswer (answer => {

    })

    info.foreach(element => {
      element._3.freeSignature()
      element._4.freeSignature()
      element._5.freeSignature()
      element._6.freeSignature()
    })
  }

  @Test
  def getMessageToSignTest(): Unit = {
    val sidechainState = mock[SidechainState]
    val history = mock[SidechainHistory]
    when(history.mainchainHeaderInfoByHash(ArgumentMatchers.any[Array[Byte]])).thenAnswer(_ => {
      val info: MainchainHeaderInfo = mock[MainchainHeaderInfo]
      when(info.cumulativeCommTreeHash).thenReturn(new Array[Byte](32))
      when(info.sidechainBlockId).thenReturn(ModifierId @@ "some_block_id")
      Some(info)
    })
    val sidechainNodeView = CurrentView(history, sidechainState, mock[SidechainWallet], mock[SidechainMemoryPool])
    Mockito.when(keyRotationStrategy.getMessageToSign(sidechainNodeView, WithKeyRotationStrategyTest.epochNumber)) thenAnswer (answer => {

    })
  }

  private def getMockedSettings(timeoutDuration: FiniteDuration, submitterIsEnabled: Boolean, signerIsEnabled: Boolean): SidechainSettings = {
    val mockedRESTSettings: RESTApiSettings = mock[RESTApiSettings]
    when(mockedRESTSettings.timeout).thenReturn(timeoutDuration)

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

  def getSchnorrPropositionsAndSchnorrProofs: Seq[(SchnorrProposition, Option[SchnorrProof])] = {
    val schnorrPropositions = getSchnorrPropositions
    val schnorrProofs = getSchnorrProofs
    for (i <- schnorrPropositions.indices) yield {
      (schnorrPropositions(i), Some(schnorrProofs(i)))
    }
  }

  private def getSignatures: Seq[Array[Byte]] = {
    Seq(
      "04febee1e9a65ea8db24895983df590d4eae773db08a855371f10cc0b8cdd82037c8ead12135978647414bf8faec77ee93152835670f400b2c33045083119d8e",
      "045657a5eb023dcaccd8b06abeb44027a13f18d3992afa82b4ebed60a83dd40b227665927af4ff1c53a263a4dabfc73f30839be8d19870e071f42f0d08dc2d95",
      "04189f0cf7ad5ef34a3354fa5892c0a90ff1714fe8390239c927a9e7cc3eab7b099353119647a2fcd63482973ae933350f343ecdba692b2a4ed3dcfda8001b14",
      "04afa320d61619e019db18980c496298d0e4b95b5f314d4ccad95f6ab505c06422326f3e96cf8c71b4eb85b19af0dc2ef11954b155a361c3e70f56e5e55614f5",
      "0460fed2e91de656cacc9abf59d12063169890dc6990cba196f77cf714cb7a3009cd0f8ddc8a85748a1d8c9a7e43268a4f9e36aafd81eedf82d8f55133b26a05",
      "04da00e77eea4897f60b6b2d0ed31ba568bdb00fe264bdc0a88a2a530a2f660f1e59b71329504d19b5765a823e46b94552a3eed48f0e8f4f4a53098ea311b327",
      "049ef25590e71757da5ada92089c70e030ccccb25173b167e8c875a9b1dca50c3c368270f22909f2e586efa1f9fceece2890f2a0724373dab271b60123cd9064"
    ).map(hexToBytes)
  }

  private def getSchnorrProofs: Seq[SchnorrProof] = {
    getSignatures.map(bytes => new SchnorrProof(bytes))
  }

  private def getPublicKeys: Seq[Array[Byte]] = {
    Seq("c8ead12135978647414bf8faec77ee93152835670f400b2c33045083119d8e3780",
      "7665927af4ff1c53a263a4dabfc73f30839be8d19870e071f42f0d08dc2d950e00",
      "9353119647a2fcd63482973ae933350f343ecdba692b2a4ed3dcfda8001b143280",
      "326f3e96cf8c71b4eb85b19af0dc2ef11954b155a361c3e70f56e5e55614f50980",
      "cd0f8ddc8a85748a1d8c9a7e43268a4f9e36aafd81eedf82d8f55133b26a051180",
      "59b71329504d19b5765a823e46b94552a3eed48f0e8f4f4a53098ea311b3272500",
      "368270f22909f2e586efa1f9fceece2890f2a0724373dab271b60123cd90640100")
      .map(hexToBytes)
  }

  private def getSchnorrPropositions: Vector[SchnorrProposition] = {
    getPublicKeys.map(bytes => new SchnorrProposition(bytes)).toVector
  }

  private def hexToBytes(hex: String): Array[Byte] = {
    hex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }
}

object WithKeyRotationStrategyTest {
  private val epochNumber = 10
  private val btrFee = 100L
  private val ftMinAmount = 200L
  val DLOG_KEYS_SIZE: Int = 1 << 18
  val CERT_SEGMENT_SIZE: Int = 1 << 15
  val CSW_SEGMENT_SIZE: Int = 1 << 18
  private val keyCount = 4
}
