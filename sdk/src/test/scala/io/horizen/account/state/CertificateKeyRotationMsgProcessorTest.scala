package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.state.CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig
import io.horizen.account.utils.FeeUtils
import io.horizen.certificatesubmitter.keys.KeyRotationProofTypes.{KeyRotationProofType, MasterKeyRotationProofType, SigningKeyRotationProofType}
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.fixtures.StoreFixture
import io.horizen.params.NetworkParams
import io.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import io.horizen.utils.{BytesUtils, ClosableResourceHandler}
import io.horizen.evm.{Address, Hash}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import sparkz.core.bytesToVersion

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

  val sidechainId: Array[Byte] = Array[Byte](0, 1, 0)
  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  when(mockNetworkParams.sidechainId).thenReturn(sidechainId)
  val certificateKeyRotationMsgProcessor: CertificateKeyRotationMsgProcessor = CertificateKeyRotationMsgProcessor(mockNetworkParams)
  val contractAddress: Address = certificateKeyRotationMsgProcessor.contractAddress

  val SubmitKeyRotation: Array[Byte] = getEventSignature("submitKeyRotation(uint32,uint32,bytes32,bytes1,bytes32,bytes32,bytes32,bytes32,bytes32,bytes32)")
  val NumberOfParams: Int = 6

  val keyGenerator: SchnorrKeyGenerator = SchnorrKeyGenerator.getInstance()

  val masterKeyType: KeyRotationProofType = MasterKeyRotationProofType
  val signingKeyType: KeyRotationProofType = SigningKeyRotationProofType

  // the payload of a command input which makes ABI decode() throw an exception
  val notDecodableData =
    "89a252fa1cc228c0c514b0adfdcc3e0ccb760a997ebe8001f7efbdf80d1425ee576e8641aeef0de718d58e1436674f648a4ae71" +
    "4ed235ad03d4c63d3966a0fa60427a5a7eb690400ea598e10087e4a915696214b6901b1c2ed4c02e65ed8c98ffa55f2c31f3275a767157c" +
    "67ec925ad783cd8e77df2cef2e2a7924694d69e4e614cdf9dff95a98b64ab91f8d6131c657163fa4dfe1bfe2c5ee7cf9b9944f339225c15" +
    "caa0f519818661564ec66ab3d0d4a58ebdc2668fdc7826f9f1e26e8eba36fb437fb58b3a52821464d6000e673580fb7cb870848fd57086a" +
    "bda73acfad092c316bd6b0bb7df0f43216ebba355415860c95690467a1f8f16d6a89cb04ea57095451575737254dec5c0f6b204b672986c" +
    "ff2cf3eb24b95879b9aca18215ad8a626aa58720fda352cfd18a6432f393c1abf33b7fa8cb2cf64b88c1724997987"

  @Before
  def setUp(): Unit = {
  }

  def generateKeys(n: Int): immutable.Seq[SchnorrSecret] = {
    (1 to n).map(_ => keyGenerator.generateSecret(Random.nextDouble().toByte.toByteArray))
  }

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = 0): Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      origin,
      Optional.of(contractAddress), // to
      0, // gasPrice
      0, // gasFeeCap
      0, // gasTipCap
      0, // gasLimit
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

  private def processKeyRotationMessage(newKey: SchnorrSecret, keyRotationProof: KeyRotationProof, view: AccountStateView, epoch: Int = 0,
                                        spuriousBytes: Option[Array[Byte]] = None, badBytes: Boolean = false): Array[Byte] = {
    val blockContext =
      if (epoch == 0)
        defaultBlockContext
      else
        new BlockContext(Address.ZERO, 0, 0, FeeUtils.GAS_LIMIT, 0, 0, epoch, 1, MockedHistoryBlockHashProvider, Hash.ZERO)

    val messageToSign = keyRotationProof.keyType match {
      case SigningKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForSigningKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
      case MasterKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForMasterKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
    }

    val encodedInputOk = SubmitKeyRotationCmdInput(keyRotationProof, newKey.sign(messageToSign)).encode()

    val encodedInput = if (badBytes) {
      BytesUtils.fromHexString(notDecodableData)
    } else {
      spuriousBytes match {
        case Some(bytes) =>
          Bytes.concat(encodedInputOk, bytes)
        case None =>
          encodedInputOk
      }
    }

    withGas {
      certificateKeyRotationMsgProcessor.process(
        getMessage(
          to = contractAddress,
          data = BytesUtils.fromHexString(SubmitKeyRotationReqCmdSig) ++ encodedInput,
          nonce = randomNonce
        ), view, _, blockContext
      )
    }
  }


  private def processBadKeyRotationMessage(newKey: SchnorrSecret, keyRotationProof: KeyRotationProof, view: AccountStateView, epoch: Int = 0,
                                           spuriousBytes: Option[Array[Byte]], badBytes: Boolean, errMsg : String) = {
    val ex = intercept[ExecutionRevertedException] {
      processKeyRotationMessage(newKey, keyRotationProof, view, epoch, spuriousBytes, badBytes)
    }
    assertTrue(ex.getMessage.contains(errMsg))
  }

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calcolated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals("Wrong MethodId for SubmitKeyRotationReqCmdSig", "288d61cc", CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig)
  }


  @Test
  def testProcessShortOpCode(): Unit = {
    usingView(certificateKeyRotationMsgProcessor) { view =>
      certificateKeyRotationMsgProcessor.init(view)
      val args: Array[Byte] = new Array[Byte](0)
      val opCode = BytesUtils.fromHexString("ac")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid (1 byte instead of 4 bytes)
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, certificateKeyRotationMsgProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Data length"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessInvalidOpCode(): Unit = {
    usingView(certificateKeyRotationMsgProcessor) { view =>
      certificateKeyRotationMsgProcessor.init(view)
      val args: Array[Byte] = BytesUtils.fromHexString("1234567890")
      val opCode = BytesUtils.fromHexString("abadc0de")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, certificateKeyRotationMsgProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Requested function does not exist"))
      view.commit(bytesToVersion(getVersion.data()))
    }
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

      // negative test: try using an input with a trailing byte
      processBadKeyRotationMessage(newMasterKey, keyRotationProof, view, spuriousBytes = Some(new Array[Byte](1)), badBytes = false, errMsg = "Wrong message data field length")
      // negative test: try using a message with right length but wrong bytes
      processBadKeyRotationMessage(newMasterKey, keyRotationProof, view, spuriousBytes = None, badBytes = true, errMsg = "Could not decode")

      // positive case
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
