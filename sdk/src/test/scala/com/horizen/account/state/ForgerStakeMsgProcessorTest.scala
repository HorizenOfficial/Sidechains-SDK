package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, GetListOfForgersCmd, RemoveStakeCmd, fakeSmartContractAddress, getMessageToSign, getStakeId}
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.consensus
import com.horizen.evm.{LevelDBDatabase, StateDB}
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.utils.{BytesUtils, ListSerializer}
import org.junit.Assert._
import org.junit._
import org.junit.rules.TemporaryFolder
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.crypto.{ECKeyPair, Keys, Sign}

import java.math.BigInteger
import scala.collection.JavaConverters.collectionAsScalaIterableConverter


class ForgerStakeMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar {

  var tempFolder = new TemporaryFolder

  val dummyBigInteger: java.math.BigInteger = java.math.BigInteger.ONE
  val negativeAmount: java.math.BigInteger = BigInteger.valueOf(-1)

  val invalidWeiAmount: java.math.BigInteger = new java.math.BigInteger("10000000001")
  val validWeiAmount: java.math.BigInteger = new java.math.BigInteger("10000000000")

  val senderProposition: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))
  val consensusEpochNumber: consensus.ConsensusEpochNumber = consensus.ConsensusEpochNumber @@ 123

  val forgingInfoSerializer = new ListSerializer[AccountForgingStakeInfo](AccountForgingStakeInfoSerializer)


  // create private/public key pair
  val pair : ECKeyPair = Keys.createEcKeyPair
  val ownerAddressProposition = new AddressProposition(BytesUtils.fromHexString(Keys.getAddress(pair)))

  @Before
  def setUp(): Unit = {
  }

  def getView: AccountStateView = {
    tempFolder.create()
    val databaseFolder = tempFolder.newFolder("evm-db" + Math.random())
    val hashNull = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")
    val db : LevelDBDatabase = new LevelDBDatabase(databaseFolder.getAbsolutePath)
    val messageProcessors: Seq[MessageProcessor] = Seq()
    val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb: StateDB = new StateDB(db, hashNull)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  def getDefaultMessage(opCode: Array[Byte], arguments: Array[Byte], nonce: BigInteger, value: BigInteger = negativeAmount) : Message = {
    val data = Bytes.concat(opCode, arguments)
    new Message(
      senderProposition,
      ForgerStakeMsgProcessor.fakeSmartContractAddress, // to
      dummyBigInteger, // gasPrice
      dummyBigInteger, // gasFeeCap
      dummyBigInteger, // gasTipCap
      dummyBigInteger, // gasLimit
      value,
      nonce,
      data)
  }

  def getRandomNonce: BigInteger = {
    val codeHash = new Array[Byte](32)
    scala.util.Random.nextBytes(codeHash)
    new java.math.BigInteger(codeHash)
  }

  def removeForgerStake(stateView: AccountStateView, stakeId: Array[Byte]): Unit = {
    val nonce = getRandomNonce
    val msgToSign = getMessageToSign(stakeId, senderProposition.address(), nonce.toByteArray)
    val msgSignatureData = Sign.signMessage(msgToSign, pair, true)
    val msgSignature = new SignatureSecp256k1(msgSignatureData)

    // create command arguments
    val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)

    val data: Array[Byte] = RemoveStakeCmdInputSerializer.toBytes(removeCmdInput)

    val msg = getDefaultMessage(
      BytesUtils.fromHexString(RemoveStakeCmd),
      data, nonce)

    // try processing the removal of stake, should succeed
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
    }
  }

  def getForgerStakeList(stateView: AccountStateView) : java.util.List[AccountForgingStakeInfo] = {

    val data: Array[Byte] = new Array[Byte](0)
    val msg = getDefaultMessage(BytesUtils.fromHexString(GetListOfForgersCmd),
      data, getRandomNonce)

    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case _: InvalidMessage =>
        throw new IllegalArgumentException("")
      case _: ExecutionFailed =>
        throw new IllegalArgumentException("")

      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.RemoveStakeGasPaidValue)

        forgingInfoSerializer.parseBytesTry(res.returnData()).get
    }
  }

  def createSenderAccount(view: AccountStateView, amount: BigInteger = BigInteger.ZERO) : Unit = {
    if (!view.accountExists(senderProposition.address())) {
      val codeHash = new Array[Byte](32)
      util.Random.nextBytes(codeHash)
      view.addAccount(senderProposition.address(), codeHash)

      if (amount.compareTo(BigInteger.ZERO) >= 0)
      {
         view.addBalance(senderProposition.address(), amount)
      }
    }
  }

  @Test
  def testInit(): Unit = {
    val stateView = getView

    // we have to call init beforehand
    assertFalse(stateView.accountExists(ForgerStakeMsgProcessor.fakeSmartContractAddress.address()))

    ForgerStakeMsgProcessor.init(stateView)

    assertTrue(stateView.accountExists(ForgerStakeMsgProcessor.fakeSmartContractAddress.address()))

    stateView.stateDb.close()
  }

  @Test
  def testCanProcess(): Unit = {
    val stateView = getView

    val msg = new Message(senderProposition, ForgerStakeMsgProcessor.fakeSmartContractAddress,
      dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, new Array[Byte](0))

    assertTrue(ForgerStakeMsgProcessor.canProcess(msg, stateView))

    val invalidForgerStakeProposition = new AddressProposition(BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea"))
    val msgNotProcessable = new Message(senderProposition, invalidForgerStakeProposition,
      dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, new Array[Byte](0))
    assertFalse(ForgerStakeMsgProcessor.canProcess(msgNotProcessable, stateView))

    stateView.stateDb.close()
  }

  @Test
  def testAddAndRemoveStake(): Unit = {

    val allowedForgerList: java.util.List[ForgerPublicKeys] = new java.util.ArrayList[ForgerPublicKeys]()

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    // add the forger into the allowed list
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition, vrfPublicKey))

    val stateView = getView

    ForgerStakeMsgProcessor.init(stateView)

    // create sender account with some fund in it
    val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
    createSenderAccount(stateView, initialAmount)

    // just to have a valid epoch number
    Mockito.when(stateView.metadataStorageView.getConsensusEpochNumber).thenReturn(Some(consensusEpochNumber))

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
      ownerAddressProposition,
      allowedForgerList
    )
    val data: Array[Byte] = AddNewStakeCmdInputSerializer.toBytes(cmdInput)
    val msg = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, validWeiAmount)

    // positive case, verify we can add the stake to view
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))

      case result => Assert.fail(s"Wrong result: $result")
    }

    // verify we added the amount to smart contract and we charge the sender
    assertTrue(stateView.getBalance(fakeSmartContractAddress.address()) == validWeiAmount)
    assertTrue(stateView.getBalance(senderProposition.address()) == initialAmount.subtract(validWeiAmount))

    // try processing a msg with the same stake (same msg), should fail
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        println("This is the reason: " + res.getReason.getMessage)
      case result => Assert.fail(s"Wrong result: $result")
    }

    // try processing a msg with different stake id (diferent nonce), should succeed
    val msg2 = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, validWeiAmount)

    ForgerStakeMsgProcessor.process(msg2, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))
    }

    // verify we added the amount to smart contract and we charge the sender
    assertTrue(stateView.getBalance(fakeSmartContractAddress.address()) == validWeiAmount.multiply(BigInteger.TWO))
    assertTrue(stateView.getBalance(senderProposition.address()) == initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)))

    // remove first stake id
    val stakeId = getStakeId(msg)
    val nonce3 = getRandomNonce
    val msgToSign = getMessageToSign(stakeId, senderProposition.address(), nonce3.toByteArray)
    val msgSignatureData = Sign.signMessage(msgToSign, pair, true)
    val msgSignature = new SignatureSecp256k1(msgSignatureData)

    // create command arguments
    val removeCmdInput = RemoveStakeCmdInput(stakeId, msgSignature)

    val data3: Array[Byte] = RemoveStakeCmdInputSerializer.toBytes(removeCmdInput)

    val msg3 = getDefaultMessage(
      BytesUtils.fromHexString(RemoveStakeCmd),
      data3, nonce3)

    // try processing the removal of stake, should succeed
    ForgerStakeMsgProcessor.process(msg3, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.RemoveStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))
    }

    // verify we removed the amount from smart contract and we added it to owner (sender is not concerned)
    assertTrue(stateView.getBalance(fakeSmartContractAddress.address()) == validWeiAmount)
    assertTrue(stateView.getBalance(senderProposition.address()) == initialAmount.subtract(validWeiAmount.multiply(BigInteger.TWO)))
    assertTrue(stateView.getBalance(ownerAddressProposition.address()) == validWeiAmount)

    // try getting the list of stakes, no command arguments here, just op code
    val data4: Array[Byte] = new Array[Byte](0)
    val msg4 = getDefaultMessage(
      BytesUtils.fromHexString(GetListOfForgersCmd),
      data4, getRandomNonce)

    ForgerStakeMsgProcessor.process(msg4, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.RemoveStakeGasPaidValue)

        val forgingInfoSerializer = new ListSerializer[AccountForgingStakeInfo](AccountForgingStakeInfoSerializer)
        val returnedList = forgingInfoSerializer.parseBytesTry(res.returnData()).get

        // we should have the second stake id only
        assertTrue(returnedList.size() == 1)
        val item = returnedList.get(0)
        println("This is the returned value: " + item)
        assertTrue(BytesUtils.toHexString(item.stakeId) == BytesUtils.toHexString(getStakeId(msg2)))
        assertTrue(item.forgerStakeData.stakedAmount.equals(validWeiAmount))
    }

    stateView.stateDb.close()
  }

  @Test
  def testAddStakeNotInAllowedList(): Unit = {

    val stateView = getView

    val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1100000000000000000000000000000000000000000000000000000000000011")) // 32 bytes
    val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("110000000000000000000000000000000000000000000000000000000000000011")) // 33 bytes

    val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("2200000000000000000000000000000000000000000000000000000000000022")) // 32 bytes
    val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("220000000000000000000000000000000000000000000000000000000000000022")) // 33 bytes

    ForgerStakeMsgProcessor.init(stateView)
    createSenderAccount(stateView)

    val allowedForgerList: java.util.List[ForgerPublicKeys] = new java.util.ArrayList[ForgerPublicKeys]()

    // add the forger into the allowed list
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1))
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2))

    val notAllowedBlockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("ff22334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val notAllowedVrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("ffbbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(notAllowedBlockSignerProposition, notAllowedVrfPublicKey),
      ownerAddressProposition,
      allowedForgerList
    )
    val data: Array[Byte] = AddNewStakeCmdInputSerializer.toBytes(cmdInput)
    val msg = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, validWeiAmount)

    // should fail because forger is not in the allowed list
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
        assertTrue(res.getReason.getMessage.contains("Forger is not in the allowed list"))

      case result => Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.close()
  }

  @Test
  def testProcessInvalidOpCode(): Unit = {

    val stateView = getView

    ForgerStakeMsgProcessor.init(stateView)

    val data: Array[Byte] = BytesUtils.fromHexString("1234567890")

    val msg = getDefaultMessage(
      BytesUtils.fromHexString("03"),
      data, getRandomNonce)

    // should fail because op code is invalid
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: InvalidMessage =>
        println("This is the returned value: " + res.getReason)

      case result => Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.close()
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

    ForgerStakeMsgProcessor.init(stateView)

    val allowedForgerList: java.util.List[ForgerPublicKeys] = new java.util.ArrayList[ForgerPublicKeys]()

    // add the forger into the allowed list
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1))
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2))

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
      ownerAddressProposition,
      allowedForgerList
    )
    val data: Array[Byte] = AddNewStakeCmdInputSerializer.toBytes(cmdInput)


    val msg = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, invalidWeiAmount)// gasLimit

    Mockito.when(stateView.metadataStorageView.getConsensusEpochNumber).thenReturn(Some(consensusEpochNumber))

    // should fail because staked amount is not a zat amount
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + res.getReason)

      case result => Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.close()
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

    ForgerStakeMsgProcessor.init(stateView)

    val allowedForgerList: java.util.List[ForgerPublicKeys] = new java.util.ArrayList[ForgerPublicKeys]()

    // add the forger into the allowed list
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1))
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2))

    createSenderAccount(stateView, BigInteger.ZERO)

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
      ownerAddressProposition,
      allowedForgerList
    )
    val data: Array[Byte] = AddNewStakeCmdInputSerializer.toBytes(cmdInput)

    val msg = getDefaultMessage(
      BytesUtils.fromHexString(AddNewStakeCmd),
      data, getRandomNonce, validWeiAmount)

    Mockito.when(stateView.metadataStorageView.getConsensusEpochNumber).thenReturn(Some(consensusEpochNumber))

    // should fail because staked amount is not a zat amount
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: ExecutionFailed =>
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + res.getReason)

      case result =>
        Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.close()
  }

  @Test
  def testExtraBytesInGetListCmd(): Unit = {

    val allowedForgerList: java.util.List[ForgerPublicKeys] = new java.util.ArrayList[ForgerPublicKeys]()

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    // add the forger into the allowed list
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition, vrfPublicKey))

    val stateView = getView

    ForgerStakeMsgProcessor.init(stateView)

    // create sender account with some fund in it
    val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
    createSenderAccount(stateView, initialAmount)

    // try getting the list of stakes with some extra byte after op code (should fail)
    val data: Array[Byte] = new Array[Byte](1)
    val msg = getDefaultMessage(
      BytesUtils.fromHexString(GetListOfForgersCmd),
      data, getRandomNonce)

    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: InvalidMessage =>
        println(res.getReason.getMessage)
      case result =>
        Assert.fail(s"Wrong result: $result")
    }

    stateView.stateDb.close()
  }

  @Test
  def testForgerStakeLinkedList(): Unit = {

    val allowedForgerList: java.util.List[ForgerPublicKeys] = new java.util.ArrayList[ForgerPublicKeys]()

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff001234")) // 33 bytes

    // add the forger into the allowed list
    allowedForgerList.add(ForgerPublicKeys(blockSignerProposition, vrfPublicKey))

    val stateView = getView

    ForgerStakeMsgProcessor.init(stateView)

    // create sender account with some fund in it
    val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
    createSenderAccount(stateView, initialAmount)

    // just to have a valid epoch number
    Mockito.when(stateView.metadataStorageView.getConsensusEpochNumber).thenReturn(Some(consensusEpochNumber))

    val cmdInput = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
      ownerAddressProposition,
      allowedForgerList
    )
    val data: Array[Byte] = AddNewStakeCmdInputSerializer.toBytes(cmdInput)

    var totalForgersAmount = BigInteger.ZERO

    // add 4 forger stakes with increasing amount
    for (i <- 1 to 4) {
      val stakeAmount = validWeiAmount.multiply(BigInteger.valueOf(i))
      totalForgersAmount = totalForgersAmount.add(stakeAmount)
        val msg = getDefaultMessage(
          BytesUtils.fromHexString(AddNewStakeCmd),
          data, getRandomNonce, stakeAmount)
        ForgerStakeMsgProcessor.process(msg, stateView) match {
          case res: ExecutionSucceeded =>
            assertTrue(res.hasReturnData)
            assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
          case result => Assert.fail(s"Wrong result: $result")
        }
    }

    var forgerList = getForgerStakeList(stateView)
    assertTrue(forgerList.size() == 4)
    val listTotalAmount = forgerList.asScala.foldLeft(BigInteger.ZERO)(
      (amount, forgerStake) => forgerStake.forgerStakeData.stakedAmount.add(amount) )
    assertTrue(listTotalAmount == totalForgersAmount)

    // remove in the middle of the list
    var pair = checkRemoveItemFromList(stateView, forgerList, 2, totalForgersAmount)
    forgerList = pair._1
    totalForgersAmount = pair._2


    // remove at the beginning of the list (head)
    pair = checkRemoveItemFromList(stateView, forgerList, 0, totalForgersAmount)
    forgerList = pair._1
    totalForgersAmount = pair._2


    // remove at the end of the list
    pair = checkRemoveItemFromList(stateView, forgerList, 1, totalForgersAmount)
    forgerList = pair._1
    totalForgersAmount = pair._2


    // remove the last element we have
    val stakeIdToRemove = forgerList.get(0).stakeId
    removeForgerStake(stateView, stakeIdToRemove)
    forgerList = getForgerStakeList(stateView)
    assertTrue(forgerList.size() == 0)

    stateView.stateDb.close()
  }

  def checkRemoveItemFromList(stateView: AccountStateView, inputList: java.util.List[AccountForgingStakeInfo],
                      itemPosition: Int, totAmount: BigInteger) : (java.util.List[AccountForgingStakeInfo], BigInteger) =
    {
      // get the info related to the item to remove
      val stakeInfo = inputList.get(itemPosition)
      val stakeIdToRemove = stakeInfo.stakeId
      val stakedAmountToRemove = stakeInfo.forgerStakeData.stakedAmount

      // call msg processor for removing the selected stake
      removeForgerStake(stateView, stakeIdToRemove)

      // call msg pocessor for retrieving the resulting list of forgers
      val returnedList = getForgerStakeList(stateView)

      // check the results:
      //  we removed just one element
      assertTrue(returnedList.size() == inputList.size()-1)

      // we have now the expected total forger stake amuont
      val listTotalAmount = returnedList.asScala.foldLeft(BigInteger.ZERO)(
        (amount, forgerStake) => forgerStake.forgerStakeData.stakedAmount.add(amount) )
      assertTrue(listTotalAmount == totAmount.subtract(stakedAmountToRemove))

      // return the list just retrieved and the current total forgers stake
      (returnedList, listTotalAmount)
    }
}
