package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig
import com.horizen.account.utils.FeeUtils
import com.horizen.certificatesubmitter.keys.KeyRotationProofTypes.{KeyRotationProofType, MasterKeyRotationProofType, SigningKeyRotationProofType}
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.fixtures.StoreFixture
import com.horizen.params.NetworkParams
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import com.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger
import java.util.Optional
import scala.collection.immutable
import scala.util.Random

class CertificateKeyRotationMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with Matchers
    with MessageProcessorFixture
    with ClosableResourceHandler
    with StoreFixture {

  val bigIntegerZero: BigInteger = BigInteger.ZERO
  val sidechainId: Array[Byte] = Array[Byte](0, 1, 0)
  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  when(mockNetworkParams.sidechainId).thenReturn(sidechainId)
  val certificateKeyRotationMsgProcessor: CertificateKeyRotationMsgProcessor = CertificateKeyRotationMsgProcessor(mockNetworkParams)
  val contractAddress: Array[Byte] = certificateKeyRotationMsgProcessor.contractAddress

  val SubmitKeyRotation: Array[Byte] = getEventSignature("submitKeyRotation(uint32,uint32,bytes32,bytes1,bytes32,bytes32,bytes32,bytes32,bytes32,bytes32)")
  val NumberOfParams: Int = 6

  val keyGenerator: SchnorrKeyGenerator = SchnorrKeyGenerator.getInstance()

  val masterKeyType: KeyRotationProofType = MasterKeyRotationProofType
  val signingKeyType: KeyRotationProofType = SigningKeyRotationProofType

  @Before
  def setUp(): Unit = {
  }

  def generateKeys(n: Int): immutable.Seq[SchnorrSecret] = {
    (1 to n).map(_ => keyGenerator.generateSecret(Random.nextDouble().toByte.toByteArray))
  }

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = bigIntegerZero): Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      Optional.of(new AddressProposition(origin)),
      Optional.of(new AddressProposition(contractAddress)), // to
      bigIntegerZero, // gasPrice
      bigIntegerZero, // gasFeeCap
      bigIntegerZero, // gasTipCap
      bigIntegerZero, // gasLimit
      value,
      nonce,
      data,
      false)
  }

  def randomNonce: BigInteger = randomU256


  def createSenderAccount(view: AccountStateView, amount: BigInteger = BigInteger.ZERO): Unit = {
    if (!view.accountExists(origin)) {
      view.addAccount(origin, randomHash)

      if (amount.signum() == 1) {
        view.addBalance(origin, amount)
      }
    }
  }

  private def buildKeyRotationProof(keyType: KeyRotationProofType, index: Int, epoch: Int, newKey: SchnorrSecret, oldSigningKey: SchnorrSecret, oldMasterKey: SchnorrSecret) = {
    val messageToSign = keyType match {
      case SigningKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForSigningKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
      case MasterKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForMasterKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
    }

    KeyRotationProof(
      keyType,
      index,
      newKey.publicImage(),
      oldSigningKey.sign(messageToSign),
      oldMasterKey.sign(messageToSign)
    )
  }

  private def processKeyRotationMessage(newKey: SchnorrSecret, keyRotationProof: KeyRotationProof, view: AccountStateView, epoch: Int = 0) = {
    val blockContext =
      if (epoch == 0)
        defaultBlockContext
      else
        new BlockContext(Array.fill(20)(0), 0, 0, FeeUtils.GAS_LIMIT, 0, 0, epoch, 1)

    val messageToSign = keyRotationProof.keyType match {
      case SigningKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForSigningKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
      case MasterKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForMasterKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
    }

    withGas {
      certificateKeyRotationMsgProcessor.process(
        getMessage(
          to = contractAddress,
          data = BytesUtils.fromHexString(SubmitKeyRotationReqCmdSig) ++
            SubmitKeyRotationCmdInput(keyRotationProof, newKey.sign(messageToSign)).encode(),
          nonce = randomNonce
        ), view, _, blockContext
      )
    }
  }

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calcolated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals("Wrong MethodId for SubmitKeyRotationReqCmdSig", "288d61cc", CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig)
  }

  @Test
  def testSubmitKeyRotation(): Unit = {
    val Seq(
    oldMasterKey,
    oldSigningKey,
    newMasterKey
    ) = generateKeys(3)

    val keyRotationProof = buildKeyRotationProof(masterKeyType, index = 0, epoch = 0, newMasterKey, oldSigningKey, oldMasterKey)

    usingView(certificateKeyRotationMsgProcessor) { view =>

      certificateKeyRotationMsgProcessor.init(view)
      when(mockNetworkParams.signersPublicKeys).thenReturn(Seq(oldSigningKey.publicImage()))
      when(mockNetworkParams.mastersPublicKeys).thenReturn(Seq(oldMasterKey.publicImage()))

      processKeyRotationMessage(newMasterKey, keyRotationProof, view)

      certificateKeyRotationMsgProcessor.getKeysRotationHistory(masterKeyType, index = 0, view) shouldBe KeyRotationHistory(epochNumbers = List(0))
      certificateKeyRotationMsgProcessor.getKeysRotationHistory(signingKeyType, index = 0, view) shouldBe KeyRotationHistory(epochNumbers = List())

      certificateKeyRotationMsgProcessor.getKeyRotationProof(0, 0, masterKeyType, view) shouldBe Some(keyRotationProof)

      certificateKeyRotationMsgProcessor.getCertifiersKeys(-1, view) shouldBe
        CertifiersKeys(
          Vector(oldSigningKey.publicImage()),
          Vector(oldMasterKey.publicImage())
        )

      certificateKeyRotationMsgProcessor.getCertifiersKeys(0, view) shouldBe
        CertifiersKeys(
          Vector(oldSigningKey.publicImage()),
          Vector(newMasterKey.publicImage())
        )
    }
  }

  @Test
  def testKeyRotationOverride(): Unit = {
    val Seq(
    oldMasterKey,
    oldSigningKey,
    newMasterKeyFirst,
    newMasterKeySecond
    ) = generateKeys(4)

    val keyRotationProofFirst = buildKeyRotationProof(masterKeyType, index = 0, epoch = 0, newMasterKeyFirst, oldSigningKey, oldMasterKey)
    val keyRotationProofSecond = buildKeyRotationProof(masterKeyType, index = 0, epoch = 0, newMasterKeySecond, oldSigningKey, oldMasterKey)

    usingView(certificateKeyRotationMsgProcessor) { view =>

      certificateKeyRotationMsgProcessor.init(view)
      when(mockNetworkParams.signersPublicKeys).thenReturn(Seq(oldSigningKey.publicImage()))
      when(mockNetworkParams.mastersPublicKeys).thenReturn(Seq(oldMasterKey.publicImage()))

      processKeyRotationMessage(newMasterKeyFirst, keyRotationProofFirst, view)
      processKeyRotationMessage(newMasterKeySecond, keyRotationProofSecond, view)

      certificateKeyRotationMsgProcessor.getKeysRotationHistory(masterKeyType, index = 0, view) shouldBe KeyRotationHistory(epochNumbers = List(0))
      certificateKeyRotationMsgProcessor.getKeysRotationHistory(signingKeyType, index = 0, view) shouldBe KeyRotationHistory(epochNumbers = List())

      certificateKeyRotationMsgProcessor.getKeyRotationProof(0, 0, masterKeyType, view) shouldBe Some(keyRotationProofSecond)

      certificateKeyRotationMsgProcessor.getCertifiersKeys(-1, view) shouldBe
        CertifiersKeys(
          Vector(oldSigningKey.publicImage()),
          Vector(oldMasterKey.publicImage())
        )

      certificateKeyRotationMsgProcessor.getCertifiersKeys(0, view) shouldBe
        CertifiersKeys(
          Vector(oldSigningKey.publicImage()),
          Vector(newMasterKeySecond.publicImage())
        )
    }
  }

  @Test
  def testKeyRotationSeveralEpochs(): Unit = {
    /*
    SETUP:
    config with 2 signing keys and 2 master keys
    master key #0 is changed in epoch 0, 1, 2
    signing key #0 is changed in epoch 1
    master key #1 is changed in epoch 2
    signing key #1 is never changed

    VERIFICATIONS:
    for epoch 0 all keys from config
    for epoch 1 master key #0 is changed
    for epoch 2 master key #0 is changed twice and signing key #0 is changed
     */
    val Seq(
    oldMasterKey0,
    oldMasterKey1,
    oldSigningKey0,
    oldSigningKey1,
    masterKey0FirstUpdate,
    masterKey0SecondUpdate,
    masterKey0ThirdUpdate,
    signingKey0Updated,
    masterKey1Updated,
    ) = generateKeys(9)

    val keyRotationEpoch_0_MK_0 = buildKeyRotationProof(masterKeyType, index = 0, epoch = 0, masterKey0FirstUpdate, oldSigningKey0, oldMasterKey0)

    val keyRotationEpoch_1_MK_0 = buildKeyRotationProof(masterKeyType, index = 0, epoch = 1, masterKey0SecondUpdate, oldSigningKey0, masterKey0FirstUpdate)
    val keyRotationEpoch_1_SK_0 = buildKeyRotationProof(signingKeyType, index = 0, epoch = 1, signingKey0Updated, oldSigningKey0, masterKey0FirstUpdate)

    val keyRotationEpoch_2_MK_0 = buildKeyRotationProof(masterKeyType, index = 0, epoch = 2, masterKey0ThirdUpdate, signingKey0Updated, masterKey0SecondUpdate)
    val keyRotationEpoch_2_MK_1 = buildKeyRotationProof(masterKeyType, index = 1, epoch = 2, masterKey1Updated, oldSigningKey1, oldMasterKey1)

    usingView(certificateKeyRotationMsgProcessor) { view =>

      certificateKeyRotationMsgProcessor.init(view)
      when(mockNetworkParams.mastersPublicKeys).thenReturn(Seq(oldMasterKey0, oldMasterKey1).map(_.publicImage()))
      when(mockNetworkParams.signersPublicKeys).thenReturn(Seq(oldSigningKey0, oldSigningKey1).map(_.publicImage()))
      //epoch 0
      processKeyRotationMessage(masterKey0FirstUpdate, keyRotationEpoch_0_MK_0, view)
      //epoch 1
      processKeyRotationMessage(masterKey0SecondUpdate, keyRotationEpoch_1_MK_0, view, epoch = 1)
      processKeyRotationMessage(signingKey0Updated, keyRotationEpoch_1_SK_0, view, epoch = 1)
      //epoch 2
      processKeyRotationMessage(masterKey0ThirdUpdate, keyRotationEpoch_2_MK_0, view, epoch = 2)
      processKeyRotationMessage(masterKey1Updated, keyRotationEpoch_2_MK_1, view, epoch = 2)

      //asserts:
      certificateKeyRotationMsgProcessor.getKeysRotationHistory(masterKeyType, index = 0, view) shouldBe KeyRotationHistory(epochNumbers = List(2, 1, 0))
      certificateKeyRotationMsgProcessor.getKeysRotationHistory(signingKeyType, index = 0, view) shouldBe KeyRotationHistory(epochNumbers = List(1))
      certificateKeyRotationMsgProcessor.getKeysRotationHistory(masterKeyType, index = 1, view) shouldBe KeyRotationHistory(epochNumbers = List(2))
      certificateKeyRotationMsgProcessor.getKeysRotationHistory(signingKeyType, index = 1, view) shouldBe KeyRotationHistory(epochNumbers = List())

      certificateKeyRotationMsgProcessor.getCertifiersKeys(-1, view) shouldBe
        CertifiersKeys(
          Vector(oldSigningKey0, oldSigningKey1).map(_.publicImage()),
          Vector(oldMasterKey0, oldMasterKey1).map(_.publicImage())
        )

      certificateKeyRotationMsgProcessor.getCertifiersKeys(0, view) shouldBe
        CertifiersKeys(
          Vector(oldSigningKey0, oldSigningKey1).map(_.publicImage()),
          Vector(masterKey0FirstUpdate, oldMasterKey1).map(_.publicImage())
        )

      certificateKeyRotationMsgProcessor.getCertifiersKeys(1, view) shouldBe
        CertifiersKeys(
          Vector(signingKey0Updated, oldSigningKey1).map(_.publicImage()),
          Vector(masterKey0SecondUpdate, oldMasterKey1).map(_.publicImage())
        )

      certificateKeyRotationMsgProcessor.getKeyRotationProof(0, 0, masterKeyType, view) shouldBe Some(keyRotationEpoch_0_MK_0)
      certificateKeyRotationMsgProcessor.getKeyRotationProof(1, 0, masterKeyType, view) shouldBe Some(keyRotationEpoch_1_MK_0)
      certificateKeyRotationMsgProcessor.getKeyRotationProof(2, 0, masterKeyType, view) shouldBe Some(keyRotationEpoch_2_MK_0)
    }
  }
}
