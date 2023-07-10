package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.state.CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig
import io.horizen.certificatesubmitter.keys.KeyRotationProofTypes.{KeyRotationProofType, MasterKeyRotationProofType, SigningKeyRotationProofType}
import io.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof}
import io.horizen.cryptolibprovider.CryptoLibProvider
import io.horizen.evm.{Address, Hash}
import io.horizen.fixtures.StoreFixture
import io.horizen.params.NetworkParams
import io.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import io.horizen.utils.{BytesUtils, ClosableResourceHandler}
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
    "89a252fa1cc228c0c514b0adfdcc3e0ccb760a997ebe8001f7efbdf80d1425ee" +
    "576e8641aeef0de718d58e1436674f648a4ae714ed235ad03d4c63d3966a0fa6" +
    "0427a5a7eb690400ea598e10087e4a915696214b6901b1c2ed4c02e65ed8c98f" +
    "fa55f2c31f3275a767157c67ec925ad783cd8e77df2cef2e2a7924694d69e4e6" +
    "14cdf9dff95a98b64ab91f8d6131c657163fa4dfe1bfe2c5ee7cf9b9944f3392" +
    "25c15caa0f519818661564ec66ab3d0d4a58ebdc2668fdc7826f9f1e26e8eba3" +
    "6fb437fb58b3a52821464d6000e673580fb7cb870848fd57086abda73acfad09" +
    "2c316bd6b0bb7df0f43216ebba355415860c95690467a1f8f16d6a89cb04ea57" +
    "095451575737254dec5c0f6b204b672986cff2cf3eb24b95879b9aca18215ad8" +
    "a626aa58720fda352cfd18a6432f393c1abf33b7fa8cb2cf64b88c1724997987"

  // a bad value for the enum of proof type (7) is detectd by decode() as well
  val badTypeData =
    "0000000000000000000000000000000000000000000000000000000000000007" +
    "0000000000000000000000000000000000000000000000000000000000000000" +
    "242a6d34802ecd7470cdeae47756b52d33a61052cdb9e5d47599c38fdef8e319" +
    "0000000000000000000000000000000000000000000000000000000000000000" +
    "9775594a015fd0dcb4c6a8207302d50a6048560257d8018f130e20d8e4861e00" +
    "aafc31eb0d4f1cd8d47a1cf9057be504eb12bff5799a8d930fd30b58e060b509" +
    "7b6755e256e8cd4f4cdb0fe07bfa4c8575bcd2f2ad4a09b242ae4f5504bbd406" +
    "1a077c51f41d7b16f1d4da51c4e6a4e4f07b9a8e6a902cad80d3af850ca81d05" +
    "dbecbb38bdc334aa449803aecf66fd220b65ec328dbdbcd67f4125f488ed2b34" +
    "c0a18b2fcd1e4f496d6f2e786804a795ff771d8c6e20d7d8fd58a214e1e7bf2c"

  // a zero-bytes schnorr key lead to a failure in crypto lib
  val badSchnorrKeyData =
    "0000000000000000000000000000000000000000000000000000000000000001" +
    "0000000000000000000000000000000000000000000000000000000000000000" +
    "0000000000000000000000000000000000000000000000000000000000000000" +
    "0000000000000000000000000000000000000000000000000000000000000000" +
    "9775594a015fd0dcb4c6a8207302d50a6048560257d8018f130e20d8e4861e00" +
    "aafc31eb0d4f1cd8d47a1cf9057be504eb12bff5799a8d930fd30b58e060b509" +
    "7b6755e256e8cd4f4cdb0fe07bfa4c8575bcd2f2ad4a09b242ae4f5504bbd406" +
    "1a077c51f41d7b16f1d4da51c4e6a4e4f07b9a8e6a902cad80d3af850ca81d05" +
    "dbecbb38bdc334aa449803aecf66fd220b65ec328dbdbcd67f4125f488ed2b34" +
    "c0a18b2fcd1e4f496d6f2e786804a795ff771d8c6e20d7d8fd58a214e1e7bf2c"

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
                                        spuriousBytes: Option[Array[Byte]] = None, badBytes: Option[Array[Byte]] = None): Array[Byte] = {
    val blockContext =
      if (epoch == 0)
        defaultBlockContext
      else
        new BlockContext(Address.ZERO, 0, 0, DefaultGasFeeFork.blockGasLimit, 0, 0, epoch, 1, MockedHistoryBlockHashProvider, Hash.ZERO)

    val messageToSign = keyRotationProof.keyType match {
      case SigningKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForSigningKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
      case MasterKeyRotationProofType => CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
        .getMsgToSignForMasterKeyUpdate(newKey.publicImage().pubKeyBytes(), epoch, sidechainId)
    }

    val encodedInputOk = SubmitKeyRotationCmdInput(keyRotationProof, newKey.sign(messageToSign)).encode()

    val encodedInput = badBytes match {
      case Some(bytes) =>
        bytes
      case None => {
        spuriousBytes match {
          case Some(bytes) =>
            Bytes.concat(encodedInputOk, bytes)
          case None =>
            encodedInputOk
        }
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
                                           spuriousBytes: Option[Array[Byte]], badBytes: Option[Array[Byte]], errMsg : String) = {
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
      certificateKeyRotationMsgProcessor.init(view, view.getConsensusEpochNumberAsInt)
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
      certificateKeyRotationMsgProcessor.init(view, view.getConsensusEpochNumberAsInt)
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

      certificateKeyRotationMsgProcessor.init(view, view.getConsensusEpochNumberAsInt)
      when(mockNetworkParams.signersPublicKeys).thenReturn(Seq(oldSigningKey.publicImage()))
      when(mockNetworkParams.mastersPublicKeys).thenReturn(Seq(oldMasterKey.publicImage()))

      // negative test: try using an input with a trailing byte
      processBadKeyRotationMessage(newMasterKey, keyRotationProof, view, spuriousBytes = Some(new Array[Byte](1)), badBytes = None, errMsg = "Wrong message data field length")
      // negative test: try using a message with right length but wrong bytes
      val badBytes1 = Some(BytesUtils.fromHexString(notDecodableData))
      processBadKeyRotationMessage(newMasterKey, keyRotationProof, view, spuriousBytes = None, badBytes = badBytes1, errMsg = "Could not decode")
      // negative test: try using a message with wrong key type
      val badBytes2 = Some(BytesUtils.fromHexString(badTypeData))
      processBadKeyRotationMessage(newMasterKey, keyRotationProof, view, spuriousBytes = None, badBytes = badBytes2, errMsg = "Could not decode")
      // negative test: try using a message with wrong key (null bytes)
      val badBytes3 = Some(BytesUtils.fromHexString(badSchnorrKeyData))
      processBadKeyRotationMessage(newMasterKey, keyRotationProof, view, spuriousBytes = None, badBytes = badBytes3, errMsg = "Key Rotation Proof is invalid")

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

      certificateKeyRotationMsgProcessor.init(view, view.getConsensusEpochNumberAsInt)
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

      certificateKeyRotationMsgProcessor.init(view, view.getConsensusEpochNumberAsInt)
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
