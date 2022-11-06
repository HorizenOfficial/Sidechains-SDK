package com.horizen.certificatesubmitter.strategies

import akka.util.Timeout
import com.horizen._
import com.horizen.block.{SidechainCreationVersions, WithdrawalEpochCertificate}
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.{CertificateSignatureInfo, SignaturesStatus}
import com.horizen.certificatesubmitter.dataproof.CertificateDataWithKeyRotation
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, SchnorrKeysSignaturesListBytes}
import com.horizen.chain.MainchainHeaderInfo
import com.horizen.fork.{ForkManagerUtil, SimpleForkConfigurator}
import com.horizen.node.util.MainchainBlockReferenceInfo
import com.horizen.params.RegTestParams
import com.horizen.proof.SchnorrProof
import com.horizen.proposition.SchnorrProposition
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import org.junit.{Before, Test}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
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

    keyRotationStrategy = new WithKeyRotationStrategy(mockedSettings, params)

    val forkManagerUtil = new ForkManagerUtil()
    forkManagerUtil.initializeForkManager(new SimpleForkConfigurator(), "regtest")
  }

  @Test
  def generateProofTest(): Unit = {
    val schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      getPublicKeys,
      getPublicKeys,
      getPublicKeys,
      getPublicKeys,
      getPublicKeys,
      getPublicKeys,
      getPublicKeys,
      getPublicKeys
    )

    val certificateData = CertificateDataWithKeyRotation(
      referencedEpochNumber = 0,
      sidechainId = Array(Byte.MinValue),
      withdrawalRequests = Seq[WithdrawalRequestBox](),
      endEpochCumCommTreeHash = Array(Byte.MinValue),
      btrFee = 3L,
      ftMinAmount = 4L,
      schnorrKeyPairs = getSchnorrPropositionsAndSchnorrProofs,
      schnorrKeysSignaturesListBytes,
      previousCertificateOption = Option.empty[WithdrawalEpochCertificate],
      genesisKeysRootHash = Array(Byte.MinValue)
    )
    val pair: com.horizen.utils.Pair[Array[Byte], java.lang.Long] =
      keyRotationStrategy.generateProof(certificateData, provingFileAbsolutePath = "provingFileAbsolutePath")
  }

  @Test
  def buildCertificateDataTest(): Unit = {
    type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
    val sidechainState = mock[SidechainState]
    when(sidechainState.withdrawalRequests(_)) thenAnswer(_ => Seq[WithdrawalRequestBox]())
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
      referencedEpoch = 0,
      messageToSign = Array(135.toByte),
      knownSigs = ArrayBuffer(certificateSignatureInfo)
    )
    val result = keyRotationStrategy.buildCertificateData(sidechainNodeView, signaturesStatus: SignaturesStatus)
    // TODO check all certificate data fields
  }

  @Test
  def getMessageToSignTest(): Unit = {
    type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
    val sidechainState = mock[SidechainState]
    val sidechainNodeView: View = CurrentView(mock[SidechainHistory], sidechainState, mock[SidechainWallet], mock[SidechainMemoryPool])
    val referencedEpochNumber = 0
    val messageToSign = keyRotationStrategy.getMessageToSign(sidechainNodeView, referencedEpochNumber)
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
    for(i <- schnorrPropositions.indices) yield {
      (schnorrPropositions(i), Some(schnorrProofs(i)))
    }
  }

  private def getSchnorrProofs: Seq[SchnorrProof] = {
    Seq(
      "04febee1e9a65ea8db24895983df590d4eae773db08a855371f10cc0b8cdd82037c8ead12135978647414bf8faec77ee93152835670f400b2c33045083119d8e",
      "045657a5eb023dcaccd8b06abeb44027a13f18d3992afa82b4ebed60a83dd40b227665927af4ff1c53a263a4dabfc73f30839be8d19870e071f42f0d08dc2d95",
      "04189f0cf7ad5ef34a3354fa5892c0a90ff1714fe8390239c927a9e7cc3eab7b099353119647a2fcd63482973ae933350f343ecdba692b2a4ed3dcfda8001b14",
      "04afa320d61619e019db18980c496298d0e4b95b5f314d4ccad95f6ab505c06422326f3e96cf8c71b4eb85b19af0dc2ef11954b155a361c3e70f56e5e55614f5",
      "0460fed2e91de656cacc9abf59d12063169890dc6990cba196f77cf714cb7a3009cd0f8ddc8a85748a1d8c9a7e43268a4f9e36aafd81eedf82d8f55133b26a05",
      "04da00e77eea4897f60b6b2d0ed31ba568bdb00fe264bdc0a88a2a530a2f660f1e59b71329504d19b5765a823e46b94552a3eed48f0e8f4f4a53098ea311b327",
      "049ef25590e71757da5ada92089c70e030ccccb25173b167e8c875a9b1dca50c3c368270f22909f2e586efa1f9fceece2890f2a0724373dab271b60123cd9064"
    ).map(hexToBytes).map(bytes => new SchnorrProof(bytes))
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
