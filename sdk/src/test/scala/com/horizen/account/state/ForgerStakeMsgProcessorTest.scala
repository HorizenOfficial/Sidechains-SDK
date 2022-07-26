package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.account.events.{DelegateForgerStake, WithdrawForgerStake}
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, GetListOfForgersCmd, RemoveStakeCmd}
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.evm.interop.EvmLog
import com.horizen.params.NetworkParams
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import org.web3j.crypto.{ECKeyPair, Keys, Sign}
import scorex.crypto.hash.Keccak256

import java.math.BigInteger
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter


class ForgerStakeMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture {

  val dummyBigInteger: java.math.BigInteger = java.math.BigInteger.ONE
  val negativeAmount: java.math.BigInteger = BigInteger.valueOf(-1)

  val invalidWeiAmount: java.math.BigInteger = new java.math.BigInteger("10000000001")
  val validWeiAmount: java.math.BigInteger = new java.math.BigInteger("10000000000")

  val senderProposition: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))

  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val forgerStakeMessageProcessor: ForgerStakeMsgProcessor = ForgerStakeMsgProcessor(mockNetworkParams)

  // create private/public key pair
  val pair: ECKeyPair = Keys.createEcKeyPair
  val ownerAddressProposition = new AddressProposition(BytesUtils.fromHexString(Keys.getAddress(pair)))

  val AddNewForgerStakeEventSig = getEventSignature("DelegateForgerStake(address,address,bytes32,uint256)")
  val NumOfIndexedAddNewStakeEvtParams = 2
  val RemoveForgerStakeEventSig = getEventSignature("WithdrawForgerStake(address,bytes32)")
  val NumOfIndexedRemoveForgerStakeEvtParams = 1


  @Before
  def setUp(): Unit = {
  }

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = negativeAmount): Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      senderProposition,
      forgerStakeMessageProcessor.fakeSmartContractAddress, // to
      dummyBigInteger, // gasPrice
      dummyBigInteger, // gasFeeCap
      dummyBigInteger, // gasTipCap
      dummyBigInteger, // gasLimit
      value,
      nonce,
      data)
  }

  def getRandomNonce: BigInteger = {
    val nonce = new Array[Byte](32)
    scala.util.Random.nextBytes(nonce)
    new java.math.BigInteger(nonce)
  }

  def removeForgerStake(stateView: AccountStateView, stakeId: Array[Byte]): Unit = {
    val nonce = getRandomNonce
    val msgToSign = ForgerStakeMsgProcessor.getMessageToSign(stakeId, senderProposition.address(), nonce.toByteArray)
    val msgSignatureData = Sign.signMessage(msgToSign, pair, true)
    val msgSignature = new SignatureSecp256k1(msgSignatureData)

    // create command arguments
    val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)

    val data: Array[Byte] = removeCmdInput.encode()

    val msg = getDefaultMessage(
      BytesUtils.fromHexString(RemoveStakeCmd),
      data, nonce)

    // try processing the removal of stake, should succeed
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertArrayEquals(stakeId, res.returnData())
    }
  }

  def getForgerStakeList(stateView: AccountStateView): Array[Byte] = {

    val data: Array[Byte] = new Array[Byte](0)
    val msg = getDefaultMessage(BytesUtils.fromHexString(GetListOfForgersCmd),
      data, getRandomNonce)

    forgerStakeMessageProcessor.process(msg, stateView) match {
      case _: InvalidMessage =>
        throw new IllegalArgumentException("")
      case _: ExecutionFailed =>
        throw new IllegalArgumentException("")

      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.RemoveStakeGasPaidValue)

        res.returnData()
    }
  }

  def createSenderAccount(view: AccountStateView, amount: BigInteger = BigInteger.ZERO): Unit = {
    if (!view.accountExists(senderProposition.address())) {
      val codeHash = new Array[Byte](32)
      scala.util.Random.nextBytes(codeHash)
      view.addAccount(senderProposition.address(), codeHash)

      if (amount.compareTo(BigInteger.ZERO) >= 0) {
        view.addBalance(senderProposition.address(), amount)
      }
    }
  }

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calcolated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals("Wrong MethodId for GetListOfForgersCmd", "a64717f5", ForgerStakeMsgProcessor.GetListOfForgersCmd)
    assertEquals("Wrong MethodId for AddNewStakeCmd", "5ca748ff", ForgerStakeMsgProcessor.AddNewStakeCmd)
    assertEquals("Wrong MethodId for RemoveStakeCmd", "f7419d79", ForgerStakeMsgProcessor.RemoveStakeCmd)
  }


  @Test
  def testNullRecords(): Unit = {
    val stateView = getView
    forgerStakeMessageProcessor.init(stateView)

    // getting a not existing key from state DB using RAW strategy gives an array of 32 bytes filled with 0, while
    // using CHUNK strategy gives an empty array instead.
    // If this behaviour changes, the codebase must change as well

    val notExistingKey1 = Keccak256.hash("NONE1")
    stateView.removeAccountStorage(forgerStakeMessageProcessor.fakeSmartContractAddress.address(), notExistingKey1)
    val ret1 = stateView.getAccountStorage(forgerStakeMessageProcessor.fakeSmartContractAddress.address(), notExistingKey1).get
    require(new ByteArrayWrapper(ret1) == new ByteArrayWrapper(new Array[Byte](32)))

    val notExistingKey2 = Keccak256.hash("NONE2")
    stateView.removeAccountStorageBytes(forgerStakeMessageProcessor.fakeSmartContractAddress.address(), notExistingKey2)
    val ret2 = stateView.getAccountStorageBytes(forgerStakeMessageProcessor.fakeSmartContractAddress.address(), notExistingKey2).get
    require(new ByteArrayWrapper(ret2) == new ByteArrayWrapper(new Array[Byte](0)))

    stateView.stateDb.commit()
    stateView.stateDb.close()
  }


  @Test
  def testInit(): Unit = {
    val stateView = getView

    // we have to call init beforehand
    assertFalse(stateView.accountExists(forgerStakeMessageProcessor.fakeSmartContractAddress.address()))

    forgerStakeMessageProcessor.init(stateView)

    assertTrue(stateView.accountExists(forgerStakeMessageProcessor.fakeSmartContractAddress.address()))

    stateView.stateDb.commit()
    stateView.close()

  }

  @Test
  def testCanProcess(): Unit = {
    val stateView = getView

    forgerStakeMessageProcessor.init(stateView)

    val msg = new Message(senderProposition, forgerStakeMessageProcessor.fakeSmartContractAddress,
      dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, new Array[Byte](0))

    assertTrue(forgerStakeMessageProcessor.canProcess(msg, stateView))

    val invalidForgerStakeProposition = new AddressProposition(BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea"))
    val msgNotProcessable = new Message(senderProposition, invalidForgerStakeProposition,
      dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, new Array[Byte](0))
    assertFalse(forgerStakeMessageProcessor.canProcess(msgNotProcessable, stateView))

    val nullForgerStakeProposition = null
    val msgNotProcessable2 = new Message(senderProposition, nullForgerStakeProposition,
      dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, new Array[Byte](0))
    assertFalse(forgerStakeMessageProcessor.canProcess(msgNotProcessable, stateView))

    stateView.stateDb.commit()
    stateView.close()
  }


  @Test
  def testAddAndRemoveStake(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    val stateView = getView

    forgerStakeMessageProcessor.init(stateView)

    // create sender account with some fund in it
    val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
    createSenderAccount(stateView, initialAmount)

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))

    //Setting the context
    val txHash1 = Keccak256.hash("first tx")
    stateView.stateDb.setTxContext(txHash1, 10)

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
      ownerAddressProposition
    )

    val data: Array[Byte] = cmdInput.encode()
    val msg = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, validWeiAmount)

    // positive case, verify we can add the stake to view
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))

      case result => Assert.fail(s"Wrong result: $result")
    }

    // verify we added the amount to smart contract and we charge the sender
    assertTrue(stateView.getBalance(forgerStakeMessageProcessor.fakeSmartContractAddress.address()) == validWeiAmount)
    assertTrue(stateView.getBalance(senderProposition.address()) == initialAmount.subtract(validWeiAmount))

    //Checking log
    var listOfLogs = stateView.getLogs(txHash1.asInstanceOf[Array[Byte]])
    assertEquals("Wrong number of logs", 1, listOfLogs.length)
    var expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
    var expectedAddStakeEvt = DelegateForgerStake(msg.getFrom, ownerAddressProposition, expStakeId, msg.getValue)
    checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

    val txHash2 = Keccak256.hash("second tx")
    stateView.stateDb.setTxContext(txHash2, 10)
    // try processing a msg with the same stake (same msg), should fail
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        println("This is the reason: " + res.getReason.getMessage)
      case result => Assert.fail(s"Wrong result: $result")
    }

    //Checking that log doesn't change
    listOfLogs = stateView.getLogs(txHash2.asInstanceOf[Array[Byte]])
    assertEquals("Wrong number of logs", 0, listOfLogs.length)

    // try processing a msg with different stake id (different nonce), should succeed
    val msg2 = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, validWeiAmount)

    val txHash3 = Keccak256.hash("third tx")
    stateView.stateDb.setTxContext(txHash3, 10)

    val expectedLastStake = AccountForgingStakeInfo(forgerStakeMessageProcessor.getStakeId(msg2),
      ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        ownerAddressProposition, validWeiAmount))

    forgerStakeMessageProcessor.process(msg2, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))
    }

    // verify we added the amount to smart contract and we charge the sender
    assertTrue(stateView.getBalance(forgerStakeMessageProcessor.fakeSmartContractAddress.address()) == validWeiAmount.multiply(BigInteger.TWO))
    assertTrue(stateView.getBalance(senderProposition.address()) == initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)))


    //Checking log
    listOfLogs = stateView.getLogs(txHash3.asInstanceOf[Array[Byte]])
    assertEquals("Wrong number of logs", 1, listOfLogs.length)
    expStakeId = forgerStakeMessageProcessor.getStakeId(msg2)
    expectedAddStakeEvt = DelegateForgerStake(msg2.getFrom, ownerAddressProposition, expStakeId, msg2.getValue)
    checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

    // remove first stake id

    val stakeId = forgerStakeMessageProcessor.getStakeId(msg)
    val nonce3 = getRandomNonce
    val msgToSign = ForgerStakeMsgProcessor.getMessageToSign(stakeId, senderProposition.address(), nonce3.toByteArray)

    val msgSignatureData = Sign.signMessage(msgToSign, pair, true)
    val msgSignature = new SignatureSecp256k1(msgSignatureData)

    // create command arguments
    val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)

    val data3: Array[Byte] = removeCmdInput.encode()

    val msg3 = getDefaultMessage(
      BytesUtils.fromHexString(RemoveStakeCmd),
      data3, nonce3)

    val txHash4 = Keccak256.hash("forth tx")
    stateView.stateDb.setTxContext(txHash4, 10)

    // try processing the removal of stake, should succeed
    forgerStakeMessageProcessor.process(msg3, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.RemoveStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))
    }

    // verify we removed the amount from smart contract and we added it to owner (sender is not concerned)
    assertTrue(stateView.getBalance(forgerStakeMessageProcessor.fakeSmartContractAddress.address()) == validWeiAmount)
    assertTrue(stateView.getBalance(senderProposition.address()) == initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)))
    assertTrue(stateView.getBalance(ownerAddressProposition.address()) == validWeiAmount)

    //Checking log
    listOfLogs = stateView.getLogs(txHash4.asInstanceOf[Array[Byte]])
    assertEquals("Wrong number of logs", 1, listOfLogs.length)
    val expectedRemoveStakeEvent = WithdrawForgerStake(ownerAddressProposition,stakeId)
    checkRemoveForgerStakeEvent(expectedRemoveStakeEvent, listOfLogs(0))

    // try getting the list of stakes, no command arguments here, just op code
    val data4: Array[Byte] = new Array[Byte](0)
    val msg4 = getDefaultMessage(
      BytesUtils.fromHexString(GetListOfForgersCmd),
      data4, getRandomNonce)

    forgerStakeMessageProcessor.process(msg4, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.RemoveStakeGasPaidValue)

        val listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
        listOfExpectedForgerStakes.add(expectedLastStake)

        assertArrayEquals(AccountForgingStakeInfoListEncoder.encode(listOfExpectedForgerStakes), res.returnData())
    }

    stateView.stateDb.commit()
    stateView.close()
  }

  @Test
  def testAddStakeNotInAllowedList(): Unit = {

    val stateView = getView

    val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
    val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

    val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
    val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

    forgerStakeMessageProcessor.init(stateView)
    createSenderAccount(stateView)

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
      data, getRandomNonce, validWeiAmount)

    // should fail because forger is not in the allowed list
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.AddNewStakeGasPaidValue)
        assertTrue(res.getReason.getMessage.contains("Forger is not in the allowed list"))

      case result => Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.commit()
    stateView.close()
  }

  @Test
  def testProcessInvalidOpCode(): Unit = {

    val stateView = getView

    forgerStakeMessageProcessor.init(stateView)

    val data: Array[Byte] = BytesUtils.fromHexString("1234567890")

    val msg = getDefaultMessage(
      BytesUtils.fromHexString("03"),
      data, getRandomNonce)


    // should fail because op code is invalid
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        println("This is the returned value: " + res.getReason)

      case result => Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.commit()
    stateView.close()
  }

  @Test
  def testProcessInvalidFakeSmartContractAddress(): Unit = {

    val stateView = getView

    forgerStakeMessageProcessor.init(stateView)

    val data = Bytes.concat(
      BytesUtils.fromHexString(GetListOfForgersCmd),
      new Array[Byte](0))

    val msg = new Message(
      senderProposition,
      WithdrawalMsgProcessor.fakeSmartContractAddress, // wrong address
      dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger,
      validWeiAmount, getRandomNonce, data)

    // should fail because op code is invalid
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: InvalidMessage =>
        println("This is the returned value: " + res.getReason)

      case result => Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.commit()
    stateView.close()
  }


  @Test
  def testAddStakeAmountNotValid(): Unit = {
    // this test will not be meaningful anymore when all sanity checks will be performed before calling any MessageProcessor

    val stateView = getView

    // create private/public key pair
    val pair = Keys.createEcKeyPair

    val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
    val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

    val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
    val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

    val ownerAddressProposition = new AddressProposition(BytesUtils.fromHexString(Keys.getAddress(pair)))

    forgerStakeMessageProcessor.init(stateView)

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
      data, getRandomNonce, invalidWeiAmount) // gasLimit

    // should fail because staked amount is not a zat amount
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + res.getReason)

      case result => Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.commit()
    stateView.close()
  }

  @Test
  def testAddStakeFromEmptyBalanceAccount(): Unit = {

    // this test will not be meaningful anymore when all sanity checks will be performed before calling any MessageProcessor
    val stateView = getView

    // create private/public key pair
    val pair = Keys.createEcKeyPair

    val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
    val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

    val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
    val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

    val ownerAddressProposition = new AddressProposition(BytesUtils.fromHexString(Keys.getAddress(pair)))

    forgerStakeMessageProcessor.init(stateView)

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition1, vrfPublicKey1),
      (blockSignerProposition2, vrfPublicKey2)
    ))

    createSenderAccount(stateView, BigInteger.ZERO)

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
      ownerAddressProposition
    )
    val data: Array[Byte] = cmdInput.encode()

    val msg = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, validWeiAmount)

    // should fail because staked amount is not a zat amount
    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        assertTrue(res.gasUsed() == forgerStakeMessageProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + res.getReason)

      case result =>
        Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.commit()
    stateView.close()
  }

  @Test
  def testExtraBytesInGetListCmd(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    val stateView = getView

    forgerStakeMessageProcessor.init(stateView)

    // create sender account with some fund in it
    val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
    createSenderAccount(stateView, initialAmount)

    // try getting the list of stakes with some extra byte after op code (should fail)
    val data: Array[Byte] = new Array[Byte](1)
    val msg = getDefaultMessage(
      BytesUtils.fromHexString(GetListOfForgersCmd),
      data, getRandomNonce)

    forgerStakeMessageProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        println(res.getReason.getMessage)
      case result =>
        Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.commit()
    stateView.close()
  }


  @Test
  def testGetListOfForgers: Unit = {

    val expectedBlockSignerProposition = "1122334455667788112233445566778811223344556677881122334455667788" // 32 bytes
    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
    val expectedVrfKey = "aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234"
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

    Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
    Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
      (blockSignerProposition, vrfPublicKey)
    ))

    val stateView = getView

    forgerStakeMessageProcessor.init(stateView)

    // create sender account with some fund in it
    val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
    createSenderAccount(stateView, initialAmount)

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
      ownerAddressProposition
    )
    val data: Array[Byte] = cmdInput.encode()

    val listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]();

    // add 4 forger stakes with increasing amount
    for (i <- 1 to 4) {
      val stakeAmount = validWeiAmount.multiply(BigInteger.valueOf(i))
      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, getRandomNonce, stakeAmount)
      val expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
      listOfExpectedForgerStakes.add(AccountForgingStakeInfo(expStakeId,
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
          ownerAddressProposition, stakeAmount)))
      forgerStakeMessageProcessor.process(msg, stateView) match {
        case res: ExecutionSucceeded =>
          assertTrue(res.hasReturnData)
          assertTrue(res.gasUsed() == forgerStakeMessageProcessor.AddNewStakeGasPaidValue)
        case result => Assert.fail(s"Wrong result: $result")
      }
    }


    //Check getListOfForgers
    val forgerList = forgerStakeMessageProcessor.getListOfForgers(stateView)
    assertEquals(listOfExpectedForgerStakes, forgerList.asJava)

    stateView.stateDb.commit()
    stateView.stateDb.close()
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

    val stateView = getView

    forgerStakeMessageProcessor.init(stateView)

    // create sender account with some fund in it
    // val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
    val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
    createSenderAccount(stateView, initialAmount)

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
      ownerAddressProposition
    )
    val data: Array[Byte] = cmdInput.encode()


    val listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]();
    // add 10 forger stakes with increasing amount
    for (i <- 1 to 4) {
      val stakeAmount = validWeiAmount.multiply(BigInteger.valueOf(i))
      val msg = getDefaultMessage(
        BytesUtils.fromHexString(AddNewStakeCmd),
        data, getRandomNonce, stakeAmount)
      val expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
      listOfExpectedForgerStakes.add(AccountForgingStakeInfo(expStakeId,
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
          ownerAddressProposition, stakeAmount)))
      forgerStakeMessageProcessor.process(msg, stateView) match {
        case res: ExecutionSucceeded =>
          assertTrue(res.hasReturnData)
          assertTrue(res.gasUsed() == forgerStakeMessageProcessor.AddNewStakeGasPaidValue)
        case result => Assert.fail(s"Wrong result: $result")
      }
    }

    val forgerListData = getForgerStakeList(stateView)

    //Check getListOfForgers
    val expectedforgerListData = AccountForgingStakeInfoListEncoder.encode(listOfExpectedForgerStakes)
    assertArrayEquals(expectedforgerListData, forgerListData)


    // remove in the middle of the list
    checkRemoveItemFromList(stateView, listOfExpectedForgerStakes, 2)


    // remove at the beginning of the list (head)
    checkRemoveItemFromList(stateView, listOfExpectedForgerStakes, 0)

    // remove at the end of the list
    checkRemoveItemFromList(stateView, listOfExpectedForgerStakes, 1)

    // remove the last element we have
    checkRemoveItemFromList(stateView, listOfExpectedForgerStakes, 0)

    stateView.stateDb.commit()
    stateView.stateDb.close()
  }

  def checkRemoveItemFromList(stateView: AccountStateView, inputList: java.util.List[AccountForgingStakeInfo],
                              itemPosition: Int) = {
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

  def checkAddNewForgerStakeEvent(expectedEvent: DelegateForgerStake, actualEvent: EvmLog) = {
    assertArrayEquals("Wrong address", forgerStakeMessageProcessor.fakeSmartContractAddress.address(), actualEvent.address.toBytes)
    assertEquals("Wrong number of topics", NumOfIndexedAddNewStakeEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", AddNewForgerStakeEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong from address in topic", expectedEvent.from, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.from.getTypeAsString)))
    assertEquals("Wrong owner address in topic", expectedEvent.owner, decodeEventTopic(actualEvent.topics(2), TypeReference.makeTypeReference(expectedEvent.owner.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(TypeReference.makeTypeReference(expectedEvent.stakeId.getTypeAsString), TypeReference.makeTypeReference(expectedEvent.value.getTypeAsString)).asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong amount in data", expectedEvent.stakeId, listOfDecodedData.get(0))
    assertEquals("Wrong stakeId in data", expectedEvent.value, listOfDecodedData.get(1))

  }


  def checkRemoveForgerStakeEvent(expectedEvent: WithdrawForgerStake, actualEvent: EvmLog) = {
    assertArrayEquals("Wrong address", forgerStakeMessageProcessor.fakeSmartContractAddress.address(), actualEvent.address.toBytes)
    assertEquals("Wrong number of topics", NumOfIndexedRemoveForgerStakeEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", RemoveForgerStakeEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong owner address in topic", expectedEvent.owner, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.owner.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(TypeReference.makeTypeReference(expectedEvent.stakeId.getTypeAsString)).asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong stakeId in data", expectedEvent.stakeId, listOfDecodedData.get(0))

  }



}
