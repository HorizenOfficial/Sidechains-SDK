package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import io.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, GetListOfForgersCmd, OpenStakeForgerListCmd, RemoveStakeCmd}
import io.horizen.account.state.events.{DelegateForgerStake, OpenForgerList, WithdrawForgerStake}
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.utils.{EthereumTransactionDecoder, ZenWeiConverter}
import io.horizen.evm.Address
import io.horizen.fixtures.StoreFixture
import io.horizen.params.NetworkParams
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.secret.PrivateKey25519
import io.horizen.utils.{BytesUtils, Ed25519}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256
import sparkz.util.serialization.VLQByteBufferReader

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util
import java.util.{Optional, Random}
import scala.collection.JavaConverters.seqAsJavaListConverter

class ForgerStakeMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture
    with StoreFixture {

  val dummyBigInteger: BigInteger = BigInteger.ONE
  val negativeAmount: BigInteger = BigInteger.valueOf(-1)

  val invalidWeiAmount: BigInteger = new BigInteger("10000000001")
  val validWeiAmount: BigInteger = new BigInteger("10000000000")

  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val forgerStakeMessageProcessor: ForgerStakeMsgProcessor = ForgerStakeMsgProcessor(mockNetworkParams)
  /** short hand: forger state native contract address */
  val contractAddress: Address = forgerStakeMessageProcessor.contractAddress

  // create private/public key pair
  val privateKey: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest".getBytes(StandardCharsets.UTF_8))
  val ownerAddressProposition: AddressProposition = privateKey.publicImage()

  val AddNewForgerStakeEventSig: Array[Byte] = getEventSignature("DelegateForgerStake(address,address,bytes32,uint256)")
  val NumOfIndexedAddNewStakeEvtParams = 2
  val RemoveForgerStakeEventSig: Array[Byte] = getEventSignature("WithdrawForgerStake(address,bytes32)")
  val NumOfIndexedRemoveForgerStakeEvtParams = 1
  val OpenForgerStakeListEventSig: Array[Byte] = getEventSignature("OpenForgerList(uint32,address,bytes32)")
  val NumOfIndexedOpenForgerStakeListEvtParams = 1


  @Before
  def setUp(): Unit = {
  }

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = negativeAmount): Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      origin,
      Optional.of(contractAddress), // to
      dummyBigInteger, // gasPrice
      dummyBigInteger, // gasFeeCap
      dummyBigInteger, // gasTipCap
      dummyBigInteger, // gasLimit
      value,
      nonce,
      data,
      false)
  }

  def randomNonce: BigInteger = randomU256

  def removeForgerStake(stateView: AccountStateView, stakeId: Array[Byte]): Unit = {
    val nonce = randomNonce

    val msgToSign = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(stakeId, origin, nonce.toByteArray)
    val msgSignature = privateKey.sign(msgToSign)

    // create command arguments
    val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)
    val data: Array[Byte] = removeCmdInput.encode()
    val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ data, nonce)

    // try processing the removal of stake, should succeed
    val returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, stateView, defaultBlockContext, _))
    assertNotNull(returnData)
    assertArrayEquals(stakeId, returnData)
  }

  def getForgerStakeList(stateView: AccountStateView): Array[Byte] = {
    val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)
    val (returnData, usedGas) = withGas { gas =>
      val result = TestContext.process(forgerStakeMessageProcessor, msg, stateView, defaultBlockContext, gas)
      (result, gas.getUsedGas)
    }
    // gas consumption depends on the number of items in the list
    assertTrue(usedGas.compareTo(0) > 0)
    assertTrue(usedGas.compareTo(50000) < 0)
    assertNotNull(returnData)
    returnData
  }

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calculated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals("Wrong MethodId for GetListOfForgersCmd", "f6ad3c23", ForgerStakeMsgProcessor.GetListOfForgersCmd)
    assertEquals("Wrong MethodId for AddNewStakeCmd", "5ca748ff", ForgerStakeMsgProcessor.AddNewStakeCmd)
    assertEquals("Wrong MethodId for RemoveStakeCmd", "f7419d79", ForgerStakeMsgProcessor.RemoveStakeCmd)
    //TODO OpenStakeForgerListCmd signature is wrong, it misses a closing parenthesis. Fixing it requires an hard fork so
    // for the moment we stick with the wrong one.
    assertEquals("Wrong MethodId for OpenStakeForgerListCmd", "b05bf06c", ForgerStakeMsgProcessor.OpenStakeForgerListCmd)
  }

  @Test
  def testInit(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>
      // we have to call init beforehand
      assertFalse(view.accountExists(contractAddress))
      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      assertTrue(view.accountExists(contractAddress))
      assertTrue(view.isSmartContractAccount(contractAddress))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testCanProcess(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // correct contract address
      assertTrue(TestContext.canProcess(forgerStakeMessageProcessor, getMessage(forgerStakeMessageProcessor.contractAddress), view, view.getConsensusEpochNumberAsInt))
      // wrong address
      assertFalse(TestContext.canProcess(forgerStakeMessageProcessor, getMessage(randomAddress), view, view.getConsensusEpochNumberAsInt))
      // contact deployment: to == null
      assertFalse(TestContext.canProcess(forgerStakeMessageProcessor, getMessage(null), view, view.getConsensusEpochNumberAsInt))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testOpenStakeForgerList(): Unit = {

    val randomGenerator = new Random(1L)
    val byteSeed = new Array[Byte](32)
    randomGenerator.nextBytes(byteSeed)
    val keyPair1 = Ed25519.createKeyPair(byteSeed)
    val blockSignSecret1: PrivateKey25519 = new PrivateKey25519(keyPair1.getKey, keyPair1.getValue)

    randomGenerator.nextBytes(byteSeed)
    val keyPair2 = Ed25519.createKeyPair(byteSeed)
    val blockSignSecret2: PrivateKey25519 = new PrivateKey25519(keyPair2.getKey, keyPair2.getValue)

    randomGenerator.nextBytes(byteSeed)
    val keyPair3 = Ed25519.createKeyPair(byteSeed)
    val blockSignSecret3: PrivateKey25519 = new PrivateKey25519(keyPair3.getKey, keyPair3.getValue)

    val blockSignerProposition1 = new PublicKey25519Proposition(keyPair1.getValue) // 32 bytes
    val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

    val blockSignerProposition2 = new PublicKey25519Proposition(keyPair2.getValue) // 32 bytes
    val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

    val blockSignerProposition3 = new PublicKey25519Proposition(keyPair3.getValue) // 32 bytes
    val vrfPublicKey3 = new VrfPublicKey(BytesUtils.fromHexString("330000000000000000000000000000000000000000000000000000000000000033")) // 33 bytes

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition1, vrfPublicKey1),
        (blockSignerProposition2, vrfPublicKey2),
        (blockSignerProposition3, vrfPublicKey3)
      ))

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      var forgerIndex = 0
      var nonce = 0
      var msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
        forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)

      var signature = blockSignSecret1.sign(msgToSign)
      var cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      var msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      var returnData = assertGas(45937, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      assertArrayEquals(Array[Byte](1, 0, 0), returnData)

      // Checking log
      val listOfLogs = view.getLogs(txHash1.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedAddStakeEvt = OpenForgerList(forgerIndex, msg.getFrom, blockSignerProposition1)
      checkOpenForgerStakeListEvent(expectedAddStakeEvt, listOfLogs(0))

      var isOpen = forgerStakeMessageProcessor.isForgerListOpen(view)
      assertFalse(isOpen)

      // negative test: add spurious byte to operation arguments
      nonce = 1
      forgerIndex = 1
      msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
        forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)

      signature = blockSignSecret2.sign(msgToSign)
      cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      val badInputEncoded = Bytes.concat(cmdInput.encode(), BytesUtils.fromHexString("aa"))

      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ badInputEncoded, nonce, ownerAddressProposition.address())

      // should fail because cmd input has a spurious trailing byte
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // negative test: use a wrong index (out of bound)
      forgerIndex = 10
      nonce = 1
      msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
        forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)

      signature = blockSignSecret2.sign(msgToSign)
      cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      // should fail because index is out of bound
      assertThrows[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }

      // negative test: use a wrong index (negative value)
      forgerIndex = -1
      nonce = 1
      // Note: this method is called from http api and by msg processor. The latter calls it after having ABI encoded
      // the input which has an Uint32 ABI type and therefore this case will never happen there
      assertThrows[IllegalArgumentException] {
        msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
          forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)
      }

      // use a good index
      forgerIndex = 1
      nonce = 1
      msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
        forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)

      // negative test: use a wrong secret for signing
      signature = blockSignSecret1.sign(msgToSign)
      cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      // should fail because signature is wrong
      assertThrows[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }

      // now sign correctly
      signature = blockSignSecret2.sign(msgToSign)
      cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      returnData = assertGas(6237, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      assertArrayEquals(Array[Byte](1, 1, 0), returnData)

      // assert we have open the forger list
      isOpen = forgerStakeMessageProcessor.isForgerListOpen(view)
      assertTrue(isOpen)

      // use the last index
      forgerIndex = 2
      nonce = 2
      msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
        forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)

      signature = blockSignSecret3.sign(msgToSign)
      cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      // should fail because the list now is open
      assertThrows[ExecutionRevertedException] {
        assertGas(4300, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }

      // assert we have open the forger list
      isOpen = forgerStakeMessageProcessor.isForgerListOpen(view)
      assertTrue(isOpen)
    }
  }

  @Test
  def testOpenStakeForgerListWithListNotRestricted(): Unit = {

    val randomGenerator = new Random(1L)
    val byteSeed = new Array[Byte](32)
    randomGenerator.nextBytes(byteSeed)
    val keyPair = Ed25519.createKeyPair(byteSeed)
    val blockSignSecret: PrivateKey25519 = new PrivateKey25519(keyPair.getKey, keyPair.getValue)


    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      Mockito.reset(mockNetworkParams)
      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(false)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq())

      val forgerIndex = 0
      val nonce = 0
      val msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
        forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)

      val signature = blockSignSecret.sign(msgToSign)
      val cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      val msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      // should fail because forger list is not restricted
      assertThrows[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }

      // assert we have open the forger list
      val isOpen = forgerStakeMessageProcessor.isForgerListOpen(view)
      assertTrue(isOpen)

    }
  }


  @Test
  def testAddAndRemoveStake(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition.address()
      )

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)
      val expectedStakeId = Keccak256.hash(Bytes.concat(
        msg.getFrom.toBytes, msg.getNonce.toByteArray, msg.getValue.toByteArray, msg.getData))

      // positive case, verify we can add the stake to view
      val returnData = assertGas(186112, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      assertNotNull(returnData)
      println("This is the returned value: " + BytesUtils.toHexString(returnData))

      // verify we added the amount to smart contract and we charge the sender
      assertArrayEquals(expectedStakeId, returnData)
      assertEquals(view.getBalance(contractAddress), validWeiAmount)
      assertEquals(view.getBalance(origin), initialAmount.subtract(validWeiAmount))

      // Checking log
      var listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      var expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
      var expectedAddStakeEvt = DelegateForgerStake(msg.getFrom, ownerAddressProposition.address(), expStakeId, msg.getValue)
      checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

      val txHash2 = Keccak256.hash("second tx")
      view.setupTxContext(txHash2, 10)
      // try processing a msg with the same stake (same msg), should fail
      assertThrows[ExecutionRevertedException](
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, _))
      )

      // Checking that log doesn't change
      listOfLogs = view.getLogs(txHash2)
      assertEquals("Wrong number of logs", 0, listOfLogs.length)

      // try processing a msg with a trailing byte in the arguments
      val badData = Bytes.concat(data, new Array[Byte](1))
      val msgBad = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ badData, randomNonce)

      // should fail because input has a trailing byte
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // try processing a msg with different stake id (different nonce), should succeed
      val msg2 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)

      val txHash3 = Keccak256.hash("third tx")
      view.setupTxContext(txHash3, 10)

      val expectedLastStake = AccountForgingStakeInfo(forgerStakeMessageProcessor.getStakeId(msg2),
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
          ownerAddressProposition, validWeiAmount))

      val returnData2 = assertGas(195012, msg2, view, forgerStakeMessageProcessor, defaultBlockContext)
      assertNotNull(returnData2)
      println("This is the returned value: " + BytesUtils.toHexString(returnData2))

      // verify we added the amount to smart contract and we charge the sender
      assertTrue(view.getBalance(contractAddress) == validWeiAmount.multiply(BigInteger.TWO))
      assertTrue(view.getBalance(origin) == initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)))

      // Checking log
      listOfLogs = view.getLogs(txHash3)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      expStakeId = forgerStakeMessageProcessor.getStakeId(msg2)
      expectedAddStakeEvt = DelegateForgerStake(msg2.getFrom, ownerAddressProposition.address(), expStakeId, msg2.getValue)
      checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

      // remove first stake id
      val stakeId = forgerStakeMessageProcessor.getStakeId(msg)
      val nonce3 = randomNonce

      val msgToSign = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(stakeId, origin, nonce3.toByteArray)
      val msgSignature = privateKey.sign(msgToSign)

      // create command arguments
      val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)

      val msg3 = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput.encode(), nonce3)
      val txHash4 = Keccak256.hash("forth tx")
      view.setupTxContext(txHash4, 10)

      // try processing the removal of stake, should succeed
      val returnData3 = assertGas(30781, msg3, view, forgerStakeMessageProcessor, defaultBlockContext)
      assertNotNull(returnData3)
      println("This is the returned value: " + BytesUtils.toHexString(returnData3))

      // verify we removed the amount from smart contract and we added it to owner (sender is not concerned)
      assertEquals(validWeiAmount, view.getBalance(contractAddress))
      assertEquals(initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)), view.getBalance(origin))
      assertEquals(validWeiAmount, view.getBalance(ownerAddressProposition.address()))

      // Checking log
      listOfLogs = view.getLogs(txHash4)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedRemoveStakeEvent = WithdrawForgerStake(ownerAddressProposition.address(), stakeId)
      checkRemoveForgerStakeEvent(expectedRemoveStakeEvent, listOfLogs(0))

      val msg4 = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)
      val returnData4 = assertGas(18900, msg4, view, forgerStakeMessageProcessor, defaultBlockContext)
      assertNotNull(returnData4)
      val listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      listOfExpectedForgerStakes.add(expectedLastStake)
      assertArrayEquals(AccountForgingStakeInfoListEncoder.encode(listOfExpectedForgerStakes), returnData4)

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testAddStakeNotInAllowedList(): Unit = {

    usingView(forgerStakeMessageProcessor) { view =>

      val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
      val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

      val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
      val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      createSenderAccount(view)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition1, vrfPublicKey1),
        (blockSignerProposition2, vrfPublicKey2)
      ))

      val notAllowedBlockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("ff22334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
      val notAllowedVrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("ffbbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(notAllowedBlockSignerProposition, notAllowedVrfPublicKey),
        ownerAddressProposition.address()
      )

      val data: Array[Byte] = cmdInput.encode()
      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, randomNonce, validWeiAmount)

      // should fail because forger is not in the allowed list
      val ex = intercept[ExecutionRevertedException] {
        assertGas(4800, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Forger is not in the allowed list"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessShortOpCode(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>
      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      val args: Array[Byte] = new Array[Byte](0)
      val opCode = BytesUtils.fromHexString("ac")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid (1 byte instead of 4 bytes)
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Data length"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessInvalidOpCode(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>
      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      val args: Array[Byte] = BytesUtils.fromHexString("1234567890")
      val opCode = BytesUtils.fromHexString("abadc0de")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid
      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("op code not supported"))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testAddStakeAmountNotValid(): Unit = {
    // this test will not be meaningful anymore when all sanity checks will be performed before calling any MessageProcessor
    usingView(forgerStakeMessageProcessor) { view =>
      // create private/public key pair
      val key: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("amounttest".getBytes(StandardCharsets.UTF_8))

      val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
      val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

      val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
      val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

      val ownerAddress = key.publicImage().address()

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition1, vrfPublicKey1),
        (blockSignerProposition2, vrfPublicKey2)
      ))

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
        ownerAddress
      )
      val data: Array[Byte] = cmdInput.encode()

      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, randomNonce, invalidWeiAmount)

      // should fail because staked amount is not a zat amount
      assertThrows[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testAddStakeWithSmartContractAsOwner(): Unit = {

    usingView(forgerStakeMessageProcessor) { view =>

      val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
      val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

      val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
      val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition1, vrfPublicKey1),
        (blockSignerProposition2, vrfPublicKey2)
      ))

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
        forgerStakeMessageProcessor.contractAddress
      )
      val data: Array[Byte] = cmdInput.encode()

      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, randomNonce, validWeiAmount)

       // should fail because recipient is a smart contract
      assertThrows[ExecutionRevertedException] {
        assertGas(200, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testExtraBytesInGetListCmd(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      // try getting the list of stakes with some extra byte after op code (should fail)
      val data: Array[Byte] = new Array[Byte](1)
      val msg = getDefaultMessage(
        BytesUtils.fromHexString(GetListOfForgersCmd),
        data, randomNonce, value = BigInteger.ZERO)

      val ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("invalid msg data length"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testGetListOfForgers(): Unit = {

    val expectedBlockSignerProposition = "1122334455667788112233445566778811223344556677881122334455667788" // 32 bytes
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
    val expectedVrfKey = "d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180"
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition.address()
      )
      val data: Array[Byte] = cmdInput.encode()

      val listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]()

      // add 4 forger stakes with increasing amount
      for (i <- 1 to 4) {
        val stakeAmount = validWeiAmount.multiply(BigInteger.valueOf(i))
        val msg = getMessage(contractAddress, stakeAmount,
          BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)
        val expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
        listOfExpectedForgerStakes.add(AccountForgingStakeInfo(expStakeId,
          ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
            ownerAddressProposition, stakeAmount)))

        val returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, _))
        assertNotNull(returnData)
      }

      //Check getListOfForgers
      val forgerList = forgerStakeMessageProcessor.getListOfForgersStakes(view)
      assertEquals(listOfExpectedForgerStakes, forgerList.asJava)

      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testForgerStakeLinkedList(): Unit = {

    val expectedBlockSignerProposition = "aa22334455667788112233445586778811223344556677881122334455667788" // 32 bytes
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
    val expectedVrfKey = "aabbccddeeff0099aabb87ddeeff0099aabbccddeeff0099aabbccd2aeff001234"
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      // val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition.address()
      )
      val data: Array[Byte] = cmdInput.encode()


      val listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]()
      // add 10 forger stakes with increasing amount
      for (i <- 1 to 4) {
        val stakeAmount = validWeiAmount.multiply(BigInteger.valueOf(i))
        val msg = getMessage(contractAddress, stakeAmount,
          BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)
        val expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
        listOfExpectedForgerStakes.add(AccountForgingStakeInfo(expStakeId,
          ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
            ownerAddressProposition, stakeAmount)))
        val returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, _))
        assertNotNull(returnData)
      }

      val forgerListData = getForgerStakeList(view)

      //Check getListOfForgers
      val expectedforgerListData = AccountForgingStakeInfoListEncoder.encode(listOfExpectedForgerStakes)
      assertArrayEquals(expectedforgerListData, forgerListData)

      // remove in the middle of the list
      checkRemoveItemFromList(view, listOfExpectedForgerStakes, 2)

      // remove at the beginning of the list (head)
      checkRemoveItemFromList(view, listOfExpectedForgerStakes, 0)

      // remove at the end of the list
      checkRemoveItemFromList(view, listOfExpectedForgerStakes, 1)

      // remove the last element we have
      checkRemoveItemFromList(view, listOfExpectedForgerStakes, 0)

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testInvalidRemoveStakeCmd(): Unit = {
    val expectedBlockSignerProposition = "aa22334455667788112233445586778811223344556677881122334455667788" // 32 bytes
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
    val expectedVrfKey = "aabbccddeeff0099aabb87ddeeff0099aabbccddeeff0099aabbccd2aeff001234"
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      // val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition.address()
      )
      val addNewStakeData: Array[Byte] = cmdInput.encode()

      val stakeAmount = validWeiAmount
      val addNewStakeMsg = getMessage(contractAddress, stakeAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ addNewStakeData, randomNonce)
      val expStakeId = forgerStakeMessageProcessor.getStakeId(addNewStakeMsg)
      val forgingStakeInfo = AccountForgingStakeInfo(expStakeId, ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey), ownerAddressProposition, stakeAmount))
      val addNewStakeReturnData = withGas(
        TestContext.process(forgerStakeMessageProcessor, addNewStakeMsg, view, defaultBlockContext, _)
      )
      assertNotNull(addNewStakeReturnData)

      val nonce = randomNonce

      val msgToSign = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(forgingStakeInfo.stakeId, origin, nonce.toByteArray)
      val msgSignature = privateKey.sign(msgToSign)

      // create command arguments
      val removeCmdInput = RemoveStakeCmdInput(forgingStakeInfo.stakeId, msgSignature)
      val data: Array[Byte] = removeCmdInput.encode()

      // should fail because value in msg should be 0 (value=1)
      var msg = getMessage(contractAddress, BigInteger.ONE, BytesUtils.fromHexString(RemoveStakeCmd) ++ data, nonce)
      assertThrows[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, _))
      }

      // should fail because value in msg should be 0 (value=-1)
      msg = getMessage(contractAddress, BigInteger.valueOf(-1), BytesUtils.fromHexString(RemoveStakeCmd) ++ data, nonce)
      assertThrows[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, _))
      }

      // should fail because input data has a trailing byte
      val badData = Bytes.concat(data, new Array[Byte](1))
      msg = getMessage(contractAddress, BigInteger.ZERO, BytesUtils.fromHexString(RemoveStakeCmd) ++ badData, nonce)
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // should fail when an illegal signature is provided
      val chunk1 = data.slice(0, 32) // Bytes32 - stakeId
      val chunk2 = data.slice(32, 64) // Bytes1 - v signature value
      val _ = data.slice(64, 96) // Bytes32 - r signature value
      val chunk4 = data.slice(96, data.length) // Bytes32 - s signature value

      val rndBytes = new Array[Byte](31)
      scala.util.Random.nextBytes(rndBytes)

      // corrupt the 1-32 bytes of v and r fields of the signature
      val chunk2Bad = Bytes.concat(chunk2.slice(0,1), rndBytes)
      val chunk3Bad = BytesUtils.fromHexString("48dcf38802818477dcf922bd5d23726f9627fefee1643678b8ed13dba00ecdff")
      val badData2 = Bytes.concat(chunk1, chunk2Bad, chunk3Bad, chunk4)

      // verify we still have the same signature v value because it is encoded as a Bytes1 ABI field and the padding
      // bytes are not considered when decoding
      val decodingOk = RemoveStakeCmdInputDecoder.decode(data)
      val decodingBad = RemoveStakeCmdInputDecoder.decode(badData2)
      assertEquals(decodingOk.signature.getV, decodingBad.signature.getV)

      msg = getMessage(contractAddress, BigInteger.ZERO, BytesUtils.fromHexString(RemoveStakeCmd) ++ badData2, nonce)
      val ex2 = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, _))
      }
      assertTrue(ex2.getMessage.contains("ill-formed signature"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testRemoveStakeCmdEncode(): Unit = {
    // The tx has a signature r value whose byte array representation is 31 bytes long (not 32), and this should be supported by te ABI encoding (no exceptions)
    val b = BytesUtils.fromHexString("f8878214920783011170941050072833849ff9bf49a55ff15d40f6d89651e880a440d097c30000000000000000000000000f6cf5090c089dfbd2c37eb58a70abad83dd79d2820d209f52759db97387354cd02079a382b1a165bac472be1da47a15a748039c6faf18a032bff10a83396a6a4a89b2f18880d6d73f7b6af9952b3f06cacc1283c86f0fa8")
    val reader = new VLQByteBufferReader(ByteBuffer.wrap(b))
    val ethTx = EthereumTransactionDecoder.decode(reader)

    val stakeId = Keccak256.hash("test")
    RemoveStakeCmdInput(stakeId, ethTx.getSignature).encode()

  }

  @Test
  def testRejectSendingInvalidValueToGetAllForgerStakes(): Unit = {
    val expectedBlockSignerProposition = "aa22334455667788112233445586778811223344556677881122334455667788" // 32 bytes
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
    val expectedVrfKey = "aabbccddeeff0099aabb87ddeeff0099aabbccddeeff0099aabbccd2aeff001234"
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      withGas { gas =>
        var msg = getMessage(contractAddress, BigInteger.ONE, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)

        assertThrows[ExecutionRevertedException] {
          TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, gas)
        }

        msg = getMessage(contractAddress, BigInteger.valueOf(-1), BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)

        assertThrows[ExecutionRevertedException] {
          TestContext.process(forgerStakeMessageProcessor, msg, view, defaultBlockContext, gas)
        }
      }
    }
  }

  def checkRemoveItemFromList(stateView: AccountStateView, inputList: java.util.List[AccountForgingStakeInfo],
                              itemPosition: Int): Unit = {

    // get the info related to the item to remove
    val stakeInfo = inputList.remove(itemPosition)
    val stakeIdToRemove = stakeInfo.stakeId

    // call msg processor for removing the selected stake
    removeForgerStake(stateView, stakeIdToRemove)

    // call msg processor for retrieving the resulting list of forgers
    val returnedList = getForgerStakeList(stateView)

    // check the results:
    //  we removed just one element
    val inputListData = AccountForgingStakeInfoListEncoder.encode(inputList)
    assertArrayEquals(inputListData, returnedList)
  }

  def checkAddNewForgerStakeEvent(expectedEvent: DelegateForgerStake, actualEvent: EthereumConsensusDataLog): Unit = {
    assertEquals("Wrong address", contractAddress, actualEvent.address)
    assertEquals("Wrong number of topics", NumOfIndexedAddNewStakeEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", AddNewForgerStakeEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong from address in topic", expectedEvent.from, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.from.getTypeAsString)))
    assertEquals("Wrong owner address in topic", expectedEvent.owner, decodeEventTopic(actualEvent.topics(2), TypeReference.makeTypeReference(expectedEvent.owner.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(
      TypeReference.makeTypeReference(expectedEvent.stakeId.getTypeAsString),
      TypeReference.makeTypeReference(expectedEvent.value.getTypeAsString))
      .asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong amount in data", expectedEvent.stakeId, listOfDecodedData.get(0))
    assertEquals("Wrong stakeId in data", expectedEvent.value, listOfDecodedData.get(1))
  }

  def checkRemoveForgerStakeEvent(expectedEvent: WithdrawForgerStake, actualEvent: EthereumConsensusDataLog): Unit = {
    assertEquals("Wrong address", contractAddress, actualEvent.address)
    assertEquals("Wrong number of topics", NumOfIndexedRemoveForgerStakeEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", RemoveForgerStakeEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong owner address in topic", expectedEvent.owner, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.owner.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(TypeReference.makeTypeReference(expectedEvent.stakeId.getTypeAsString)).asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong stakeId in data", expectedEvent.stakeId, listOfDecodedData.get(0))
  }


  def checkOpenForgerStakeListEvent(expectedEvent: OpenForgerList, actualEvent: EthereumConsensusDataLog): Unit = {
    assertEquals("Wrong address", contractAddress, actualEvent.address)
    assertEquals("Wrong number of topics", NumOfIndexedOpenForgerStakeListEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", OpenForgerStakeListEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong forger index in topic", expectedEvent.forgerIndex, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.forgerIndex.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(
      TypeReference.makeTypeReference(expectedEvent.from.getTypeAsString),
      TypeReference.makeTypeReference(expectedEvent.blockSignProposition.getTypeAsString),
    ).asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong from in data", expectedEvent.from, listOfDecodedData.get(0))
    assertEquals("Wrong block sign prop in data", expectedEvent.blockSignProposition, listOfDecodedData.get(1))
  }
}
