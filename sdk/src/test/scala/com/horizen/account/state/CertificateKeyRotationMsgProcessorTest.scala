package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig
import com.horizen.account.state.ForgerStakeMsgProcessor.AddNewStakeCmd
import com.horizen.fixtures.StoreFixture
import com.horizen.params.NetworkParams
import com.horizen.secret.{SchnorrKeyGenerator, SchnorrSecret}
import com.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito.when
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger
import scala.collection.immutable
import scala.util.Random

class CertificateKeyRotationMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture
    with ClosableResourceHandler
    with StoreFixture {

  val bigIntegerZero: BigInteger = BigInteger.ZERO

  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val certificateKeyRotationMsgProcessor: CertificateKeyRotationMsgProcessor = CertificateKeyRotationMsgProcessor(mockNetworkParams)
  val contractAddress: Array[Byte] = certificateKeyRotationMsgProcessor.contractAddress

  val SubmitKeyRotation: Array[Byte] = getEventSignature("submitKeyRotation(uint32,uint32,bytes32,bytes1,bytes32,bytes32,bytes32,bytes32,bytes32,bytes32)")
  val NumberOfParams: Int = 6

  val keyGenerator: SchnorrKeyGenerator = SchnorrKeyGenerator.getInstance()

  val masterKeyType: KeyRotationProofType.Value = KeyRotationProofType.MasterKeyRotationProofType
  val signingKeyType: KeyRotationProofType.Value = KeyRotationProofType.SigningKeyRotationProofType

  @Before
  def setUp(): Unit = {
  }

  def generateKeys(n: Int): immutable.Seq[SchnorrSecret] = {
    (0 to n).map(_ => keyGenerator.generateSecret(Random.nextDouble().toByte.toByteArray))
  }

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = bigIntegerZero): Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      new AddressProposition(origin),
      new AddressProposition(contractAddress), // to
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

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calcolated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals("Wrong MethodId for SubmitKeyRotationReqCmdSig", "aa5a0d75", CertificateKeyRotationMsgProcessor.SubmitKeyRotationReqCmdSig)
  }


  @Test
  def testSubmitKeyRotation(): Unit = {
    val oldMasterKey = generateKeys(1)
    val oldSigningKey = generateKeys(1)
    val newMasterKey = generateKeys(1).head

    val keyRotationProof = KeyRotationProof(
      masterKeyType,
      0,
      newMasterKey.publicImage(),
      oldSigningKey.head.sign(newMasterKey.getPublicBytes.take(32)),
      oldMasterKey.head.sign(newMasterKey.getPublicBytes.take(32))
    )

    usingView(certificateKeyRotationMsgProcessor) { view =>

      certificateKeyRotationMsgProcessor.init(view)
      when(mockNetworkParams.signersPublicKeys).thenReturn(oldSigningKey.map(_.publicImage()))
      when(mockNetworkParams.masterPublicKeys).thenReturn(oldMasterKey.map(_.publicImage()))

      val cmdInput = SubmitKeyRotationCmdInput(keyRotationProof, newMasterKey.sign(newMasterKey.getPublicBytes.take(32)))
      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(to = contractAddress, data = BytesUtils.fromHexString(SubmitKeyRotationReqCmdSig) ++ data, nonce = randomNonce)

      withGas {
        certificateKeyRotationMsgProcessor.process(msg, view, _, defaultBlockContext)
      }

      val masterKeyHistory = certificateKeyRotationMsgProcessor.getKeysRotationHistory(masterKeyType, 0, view)
      assertEquals("Key Rotation history is not updated", List(0), masterKeyHistory.epochNumbers)

      val signingKeyHistory = certificateKeyRotationMsgProcessor.getKeysRotationHistory(signingKeyType, 0, view)
      assertEquals("Key Rotation history was mistakenly updated", true, signingKeyHistory.epochNumbers.isEmpty)

      val maybeKeyRotation = certificateKeyRotationMsgProcessor.getKeyRotationProof(masterKeyType, 0, 0, view)
      assertTrue("Key rotation proof is not persisted", maybeKeyRotation.isDefined)
      assertEquals("Key rotation proof is corrupted", keyRotationProof, maybeKeyRotation.get)

      val certifiersKeys = certificateKeyRotationMsgProcessor.getCertifiersKeys(0, view)
      assertEquals("Certifiers master keys array is incorrect", oldMasterKey.map(_.publicImage()), certifiersKeys.masterKeys)
      assertEquals("Certifiers signing keys array is incorrect", oldSigningKey.map(_.publicImage()), certifiersKeys.signingKeys)

    }
  }

  @Test
  def testKeyRotationOverride(): Unit = {
    val oldMasterKey = generateKeys(1)
    val oldSigningKey = generateKeys(1)
    val newMasterKeyFirst = generateKeys(1).head
    val newMasterKeySecond = generateKeys(1).head

    val keyRotationProofFirst = KeyRotationProof(
      masterKeyType,
      0,
      newMasterKeyFirst.publicImage(),
      oldSigningKey.head.sign(newMasterKeyFirst.getPublicBytes.take(32)),
      oldMasterKey.head.sign(newMasterKeyFirst.getPublicBytes.take(32))
    )
    val keyRotationProofSecond = KeyRotationProof(
      masterKeyType,
      0,
      newMasterKeySecond.publicImage(),
      oldSigningKey.head.sign(newMasterKeySecond.getPublicBytes.take(32)),
      oldMasterKey.head.sign(newMasterKeySecond.getPublicBytes.take(32))
    )

    usingView(certificateKeyRotationMsgProcessor) { view =>

      certificateKeyRotationMsgProcessor.init(view)
      when(mockNetworkParams.signersPublicKeys).thenReturn(oldSigningKey.map(_.publicImage()))
      when(mockNetworkParams.masterPublicKeys).thenReturn(oldMasterKey.map(_.publicImage()))

      withGas {
        certificateKeyRotationMsgProcessor.process(
          getMessage(
            to = contractAddress,
            data = BytesUtils.fromHexString(SubmitKeyRotationReqCmdSig) ++
              SubmitKeyRotationCmdInput(keyRotationProofFirst, newMasterKeyFirst.sign(newMasterKeyFirst.getPublicBytes.take(32))).encode(),
            nonce = randomNonce
          ), view, _, defaultBlockContext
        )
      }
      withGas {
        certificateKeyRotationMsgProcessor.process(
          getMessage(
            to = contractAddress,
            data = BytesUtils.fromHexString(SubmitKeyRotationReqCmdSig) ++
              SubmitKeyRotationCmdInput(keyRotationProofSecond, newMasterKeySecond.sign(newMasterKeySecond.getPublicBytes.take(32))).encode(),
            nonce = randomNonce
          ), view, _, defaultBlockContext
        )
      }

      val masterKeyHistory = certificateKeyRotationMsgProcessor.getKeysRotationHistory(masterKeyType, 0, view)
      assertEquals("Key Rotation history is not updated", List(0), masterKeyHistory.epochNumbers)

      val signingKeyHistory = certificateKeyRotationMsgProcessor.getKeysRotationHistory(signingKeyType, 0, view)
      assertEquals("Key Rotation history was mistakenly updated", true, signingKeyHistory.epochNumbers.isEmpty)

      val maybeKeyRotation = certificateKeyRotationMsgProcessor.getKeyRotationProof(masterKeyType, 0, 0, view)
      assertTrue("Key rotation proof is not persisted", maybeKeyRotation.isDefined)
      assertEquals("Key rotation proof is corrupted", keyRotationProofSecond, maybeKeyRotation.get)

    }
  }
}
