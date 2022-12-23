package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.account.events.{DelegateForgerStake, WithdrawForgerStake}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, GetListOfForgersCmd, RemoveStakeCmd}
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.interop.EvmLog
import com.horizen.fixtures.StoreFixture
import com.horizen.params.NetworkParams
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils, ClosableResourceHandler}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import sparkz.core.bytesToVersion
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import java.util.Optional
import scala.collection.JavaConverters.seqAsJavaListConverter

class ForgerStakeMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture
    with ClosableResourceHandler
    with StoreFixture {

  val dummyBigInteger: BigInteger = BigInteger.ONE
  val negativeAmount: BigInteger = BigInteger.valueOf(-1)

  val invalidWeiAmount: BigInteger = new BigInteger("10000000001")
  val validWeiAmount: BigInteger = new BigInteger("10000000000")

  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val forgerStakeMessageProcessor: ForgerStakeMsgProcessor = ForgerStakeMsgProcessor(mockNetworkParams)
  /** short hand: forger state fake contract address */
  val contractAddress: Array[Byte] = forgerStakeMessageProcessor.contractAddress

  // create private/public key pair
  val privateKey: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("fakemsgprocessortest".getBytes(StandardCharsets.UTF_8))
  val ownerAddressProposition: AddressProposition = privateKey.publicImage()

  val AddNewForgerStakeEventSig: Array[Byte] = getEventSignature("DelegateForgerStake(address,address,bytes32,uint256)")
  val NumOfIndexedAddNewStakeEvtParams = 2
  val RemoveForgerStakeEventSig: Array[Byte] = getEventSignature("WithdrawForgerStake(address,bytes32)")
  val NumOfIndexedRemoveForgerStakeEvtParams = 1


  @Before
  def setUp(): Unit = {
  }

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = negativeAmount): Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      Optional.of(new AddressProposition(origin)),
      Optional.of(new AddressProposition(contractAddress)), // to
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
    val msgToSign = ForgerStakeMsgProcessor.getMessageToSign(stakeId, origin, nonce.toByteArray)
    val msgSignature = privateKey.sign(msgToSign)

    // create command arguments
    val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)
    val data: Array[Byte] = removeCmdInput.encode()
    val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ data, nonce)

    // try processing the removal of stake, should succeed
    val returnData = withGas(forgerStakeMessageProcessor.process(msg, stateView, _, defaultBlockContext))
    assertNotNull(returnData)
    assertArrayEquals(stakeId, returnData)
  }

  def getForgerStakeList(stateView: AccountStateView): Array[Byte] = {
    val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)
    val (returnData, usedGas) = withGas { gas =>
      val result = forgerStakeMessageProcessor.process(msg, stateView, gas, defaultBlockContext)
      (result, gas.getUsedGas)
    }
    // gas consumption depends on the number of items in the list
    assertTrue(usedGas.compareTo(0) > 0)
    assertTrue(usedGas.compareTo(3000) < 0)
    assertNotNull(returnData)
    returnData
  }

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
    assertEquals("Wrong MethodId for GetListOfForgersCmd", "f6ad3c23", ForgerStakeMsgProcessor.GetListOfForgersCmd)
    assertEquals("Wrong MethodId for AddNewStakeCmd", "5ca748ff", ForgerStakeMsgProcessor.AddNewStakeCmd)
    assertEquals("Wrong MethodId for RemoveStakeCmd", "f7419d79", ForgerStakeMsgProcessor.RemoveStakeCmd)
  }


  @Test
  def testNullRecords(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>
      forgerStakeMessageProcessor.init(view)

      // getting a not existing key from state DB using RAW strategy gives an array of 32 bytes filled with 0, while
      // using CHUNK strategy gives an empty array instead.
      // If this behaviour changes, the codebase must change as well

      val notExistingKey1 = Keccak256.hash("NONE1")
      view.removeAccountStorage(contractAddress, notExistingKey1)
      val ret1 = view.getAccountStorage(contractAddress, notExistingKey1)
      assertEquals(new ByteArrayWrapper(new Array[Byte](32)), new ByteArrayWrapper(ret1))

      val notExistingKey2 = Keccak256.hash("NONE2")
      view.removeAccountStorageBytes(contractAddress, notExistingKey2)
      val ret2 = view.getAccountStorageBytes(contractAddress, notExistingKey2)
      assertEquals(new ByteArrayWrapper(new Array[Byte](0)), new ByteArrayWrapper(ret2))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testInit(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>
      // we have to call init beforehand
      assertFalse(view.accountExists(contractAddress))
      forgerStakeMessageProcessor.init(view)
      assertTrue(view.accountExists(contractAddress))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testCanProcess(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view)

      // correct contract address
      assertTrue(forgerStakeMessageProcessor.canProcess(getMessage(forgerStakeMessageProcessor.contractAddress), view))
      // wrong address
      assertFalse(forgerStakeMessageProcessor.canProcess(getMessage(randomAddress), view))
      // contact deployment: to == null
      assertFalse(forgerStakeMessageProcessor.canProcess(getMessage(null), view))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testAddAndRemoveStake(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition
      )

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)

      // positive case, verify we can add the stake to view
      val returnData = assertGas(5550) {
        forgerStakeMessageProcessor.process(msg, view, _, defaultBlockContext)
      }
      assertNotNull(returnData)
      println("This is the returned value: " + BytesUtils.toHexString(returnData))

      // verify we added the amount to smart contract and we charge the sender
      assertTrue(view.getBalance(contractAddress) == validWeiAmount)
      assertTrue(view.getBalance(origin) == initialAmount.subtract(validWeiAmount))

      // Checking log
      // TODO: asInstanceOf required? gigo
      var listOfLogs = view.getLogs(txHash1.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      var expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
      var expectedAddStakeEvt = DelegateForgerStake(msg.getFrom.get, ownerAddressProposition, expStakeId, msg.getValue)
      checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

      val txHash2 = Keccak256.hash("second tx")
      view.setupTxContext(txHash2, 10)
      // try processing a msg with the same stake (same msg), should fail
      assertThrows[ExecutionFailedException](withGas(forgerStakeMessageProcessor.process(msg, view, _, defaultBlockContext)))

      // Checking that log doesn't change
      listOfLogs = view.getLogs(txHash2.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 0, listOfLogs.length)

      // try processing a msg with different stake id (different nonce), should succeed
      val msg2 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)

      val txHash3 = Keccak256.hash("third tx")
      view.setupTxContext(txHash3, 10)

      val expectedLastStake = AccountForgingStakeInfo(forgerStakeMessageProcessor.getStakeId(msg2),
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
          ownerAddressProposition, validWeiAmount))

      val returnData2 = assertGas(6050) {
        forgerStakeMessageProcessor.process(msg2, view, _, defaultBlockContext)
      }
      assertNotNull(returnData2)
      println("This is the returned value: " + BytesUtils.toHexString(returnData2))

      // verify we added the amount to smart contract and we charge the sender
      assertTrue(view.getBalance(contractAddress) == validWeiAmount.multiply(BigInteger.TWO))
      assertTrue(view.getBalance(origin) == initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)))

      // Checking log
      listOfLogs = view.getLogs(txHash3.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      expStakeId = forgerStakeMessageProcessor.getStakeId(msg2)
      expectedAddStakeEvt = DelegateForgerStake(msg2.getFrom.get(), ownerAddressProposition, expStakeId, msg2.getValue)
      checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

      // remove first stake id
      val stakeId = forgerStakeMessageProcessor.getStakeId(msg)
      val nonce3 = randomNonce
      val msgToSign = ForgerStakeMsgProcessor.getMessageToSign(stakeId, origin, nonce3.toByteArray)
      val msgSignature = privateKey.sign(msgToSign)

      // create command arguments
      val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)

      val msg3 = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput.encode(), nonce3)
      val txHash4 = Keccak256.hash("forth tx")
      view.setupTxContext(txHash4, 10)

      // try processing the removal of stake, should succeed
      val returnData3 = assertGas(4025) {
        forgerStakeMessageProcessor.process(msg3, view, _, defaultBlockContext)
      }
      assertNotNull(returnData3)
      println("This is the returned value: " + BytesUtils.toHexString(returnData3))

      // verify we removed the amount from smart contract and we added it to owner (sender is not concerned)
      assertEquals(validWeiAmount, view.getBalance(contractAddress))
      assertEquals(initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)), view.getBalance(origin))
      assertEquals(validWeiAmount, view.getBalance(ownerAddressProposition.address()))

      // Checking log
      listOfLogs = view.getLogs(txHash4.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedRemoveStakeEvent = WithdrawForgerStake(ownerAddressProposition, stakeId)
      checkRemoveForgerStakeEvent(expectedRemoveStakeEvent, listOfLogs(0))

      val msg4 = getDefaultMessage(BytesUtils.fromHexString(GetListOfForgersCmd), Array.emptyByteArray, randomNonce, BigInteger.ZERO)
      val returnData4 = assertGas(750) {
        forgerStakeMessageProcessor.process(msg4, view, _, defaultBlockContext)
      }
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

      forgerStakeMessageProcessor.init(view)
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
        ownerAddressProposition
      )

      val data: Array[Byte] = cmdInput.encode()
      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, randomNonce, validWeiAmount)

      // should fail because forger is not in the allowed list
      assertGas(1400) { gas =>
        assertThrows[ExecutionFailedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
        }
      }

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessInvalidOpCode(): Unit = {
    usingView(forgerStakeMessageProcessor) { view =>
      forgerStakeMessageProcessor.init(view)
      val data: Array[Byte] = BytesUtils.fromHexString("1234567890")
      val msg = getDefaultMessage(BytesUtils.fromHexString("03"), data, randomNonce)

      // should fail because op code is invalid
      assertGas(0)(gas =>
        assertThrows[ExecutionFailedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
        }
      )
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

      val ownerAddressProposition = key.publicImage()

      forgerStakeMessageProcessor.init(view)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition1, vrfPublicKey1),
        (blockSignerProposition2, vrfPublicKey2)
      ))

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
        ownerAddressProposition
      )
      val data: Array[Byte] = cmdInput.encode()

      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, randomNonce, invalidWeiAmount)

      // should fail because staked amount is not a zat amount
      assertGas(0) { gas =>
        assertThrows[ExecutionFailedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
        }
      }

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testAddStakeFromEmptyBalanceAccount(): Unit = {

    // this test will not be meaningful anymore when all sanity checks will be performed before calling any MessageProcessor
    usingView(forgerStakeMessageProcessor) { view =>

      // create private/public key pair
      val key: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("emptybalancetest".getBytes(StandardCharsets.UTF_8))

      val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
      val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

      val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
      val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

      val ownerAddressProposition = key.publicImage()

      forgerStakeMessageProcessor.init(view)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition1, vrfPublicKey1),
        (blockSignerProposition2, vrfPublicKey2)
      ))

      createSenderAccount(view, BigInteger.ZERO)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
        ownerAddressProposition
      )
      val data: Array[Byte] = cmdInput.encode()

      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, randomNonce, validWeiAmount)

      // should fail because staked amount is not a zat amount
      assertGas(4850) { gas =>
        assertThrows[ExecutionFailedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
        }
      }
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testAddStakeWithSmartContractAsOwner(): Unit = {

    // this test will not be meaningful anymore when all sanity checks will be performed before calling any MessageProcessor
    usingView(forgerStakeMessageProcessor) { view =>

      // create private/public key pair
      val key: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("ownertest".getBytes(StandardCharsets.UTF_8))

      val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
      val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

      val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
      val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

      val ownerAddressProposition = new AddressProposition(forgerStakeMessageProcessor.contractAddress)

      forgerStakeMessageProcessor.init(view)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition1, vrfPublicKey1),
        (blockSignerProposition2, vrfPublicKey2)
      ))

      createSenderAccount(view, BigInteger.ZERO)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
        ownerAddressProposition
      )
      val data: Array[Byte] = cmdInput.encode()

      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, randomNonce, validWeiAmount)

       assertGas(1400) { gas =>
        assertThrows[ExecutionRevertedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
        }
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

      forgerStakeMessageProcessor.init(view)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      // try getting the list of stakes with some extra byte after op code (should fail)
      val data: Array[Byte] = new Array[Byte](1)
      val msg = getDefaultMessage(
        BytesUtils.fromHexString(GetListOfForgersCmd),
        data, randomNonce)

      assertGas(0) { gas =>
        assertThrows[ExecutionFailedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
        }
      }

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testGetListOfForgers(): Unit = {

    val expectedBlockSignerProposition = "1122334455667788112233445566778811223344556677881122334455667788" // 32 bytes
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
    val expectedVrfKey = "aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234"
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition
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

        val returnData = withGas(forgerStakeMessageProcessor.process(msg, view, _, defaultBlockContext))
        assertNotNull(returnData)
      }

      //Check getListOfForgers
      val forgerList = forgerStakeMessageProcessor.getListOfForgers(view)
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

      forgerStakeMessageProcessor.init(view)

      // create sender account with some fund in it
      // val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition
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
        val returnData = withGas(forgerStakeMessageProcessor.process(msg, view, _, defaultBlockContext))
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
  def testRejectSendingInvalidValueToWithdraw(): Unit = {
    val expectedBlockSignerProposition = "aa22334455667788112233445586778811223344556677881122334455667788" // 32 bytes
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
    val expectedVrfKey = "aabbccddeeff0099aabb87ddeeff0099aabbccddeeff0099aabbccd2aeff001234"
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view)

      // create sender account with some fund in it
      // val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount)

      val cmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition
      )
      val addNewStakeData: Array[Byte] = cmdInput.encode()

      val stakeAmount = validWeiAmount
      val addNewStakeMsg = getMessage(contractAddress, stakeAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ addNewStakeData, randomNonce)
      val expStakeId = forgerStakeMessageProcessor.getStakeId(addNewStakeMsg)
      val forgingStakeInfo = AccountForgingStakeInfo(expStakeId, ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey), ownerAddressProposition, stakeAmount))
      val addNewStakeReturnData = withGas(forgerStakeMessageProcessor.process(addNewStakeMsg, view, _, defaultBlockContext))
      assertNotNull(addNewStakeReturnData)


      val nonce = randomNonce
      val msgToSign = ForgerStakeMsgProcessor.getMessageToSign(forgingStakeInfo.stakeId, origin, nonce.toByteArray)
      val msgSignature = privateKey.sign(msgToSign)

      // create command arguments
      val removeCmdInput = RemoveStakeCmdInput(forgingStakeInfo.stakeId, msgSignature)
      val data: Array[Byte] = removeCmdInput.encode()
      var msg = getMessage(contractAddress, BigInteger.ONE, BytesUtils.fromHexString(RemoveStakeCmd) ++ data, nonce)

      assertThrows[ExecutionRevertedException] {
        withGas(forgerStakeMessageProcessor.process(msg, view, _, defaultBlockContext))
      }

      msg = getMessage(contractAddress, BigInteger.valueOf(-1), BytesUtils.fromHexString(RemoveStakeCmd) ++ data, nonce)

      assertThrows[ExecutionRevertedException] {
        withGas(forgerStakeMessageProcessor.process(msg, view, _, defaultBlockContext))
      }

      view.commit(bytesToVersion(getVersion.data()))
    }
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

      forgerStakeMessageProcessor.init(view)

      withGas { gas =>
        var msg = getMessage(contractAddress, BigInteger.ONE, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)

        assertThrows[ExecutionRevertedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
        }

        msg = getMessage(contractAddress, BigInteger.valueOf(-1), BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)

        assertThrows[ExecutionRevertedException] {
          forgerStakeMessageProcessor.process(msg, view, gas, defaultBlockContext)
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

  def checkAddNewForgerStakeEvent(expectedEvent: DelegateForgerStake, actualEvent: EvmLog): Unit = {
    assertArrayEquals("Wrong address", contractAddress, actualEvent.address.toBytes)
    assertEquals("Wrong number of topics", NumOfIndexedAddNewStakeEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", AddNewForgerStakeEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong from address in topic", expectedEvent.from, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.from.getTypeAsString)))
    assertEquals("Wrong owner address in topic", expectedEvent.owner, decodeEventTopic(actualEvent.topics(2), TypeReference.makeTypeReference(expectedEvent.owner.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(TypeReference.makeTypeReference(expectedEvent.stakeId.getTypeAsString), TypeReference.makeTypeReference(expectedEvent.value.getTypeAsString)).asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong amount in data", expectedEvent.stakeId, listOfDecodedData.get(0))
    assertEquals("Wrong stakeId in data", expectedEvent.value, listOfDecodedData.get(1))
  }

  def checkRemoveForgerStakeEvent(expectedEvent: WithdrawForgerStake, actualEvent: EvmLog): Unit = {
    assertArrayEquals("Wrong address", contractAddress, actualEvent.address.toBytes)
    assertEquals("Wrong number of topics", NumOfIndexedRemoveForgerStakeEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", RemoveForgerStakeEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong owner address in topic", expectedEvent.owner, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.owner.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(TypeReference.makeTypeReference(expectedEvent.stakeId.getTypeAsString)).asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong stakeId in data", expectedEvent.stakeId, listOfDecodedData.get(0))
  }

}
