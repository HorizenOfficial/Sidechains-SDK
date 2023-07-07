package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.fork.ZenDAOFork
import io.horizen.account.state.McAddrOwnershipMsgProcessor.{AddNewOwnershipCmd, GetListOfAllOwnershipsCmd, GetListOfOwnershipsCmd, RemoveOwnershipCmd, getOwnershipId}
import io.horizen.account.state.events.{AddMcAddrOwnership, RemoveMcAddrOwnership}
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.consensus.intToConsensusEpochNumber
import io.horizen.evm.Address
import io.horizen.fixtures.StoreFixture
import io.horizen.fork.{ForkConfigurator, ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch}
import io.horizen.params.NetworkParams
import io.horizen.utils.{BytesUtils, Pair}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import java.util.Optional
import scala.jdk.CollectionConverters.seqAsJavaListConverter

class McAddrOwnershipMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture
    with StoreFixture {

  val ZENDAO_MOCK_FORK_POINT: Int = 100

  class TestOptionalForkConfigurator extends ForkConfigurator {
    override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 0)

    override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
      Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
        new Pair(SidechainForkConsensusEpoch(ZENDAO_MOCK_FORK_POINT, ZENDAO_MOCK_FORK_POINT, ZENDAO_MOCK_FORK_POINT), ZenDAOFork(true)),
      ).asJava
  }

  override val defaultBlockContext = new BlockContext(
    Address.ZERO,
    0,
    0,
    DefaultGasFeeFork.blockGasLimit,
    0,
    /*consensusEpochNumber*/ ZENDAO_MOCK_FORK_POINT,
    0,
    1,
    MockedHistoryBlockHashProvider,
    new io.horizen.evm.Hash(new Array[Byte](32))
  )

  @Before
  def init(): Unit = {
    ForkManagerUtil.initializeForkManager(new TestOptionalForkConfigurator, "regtest")
    // by default start with fork active
    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(Option(intToConsensusEpochNumber(ZENDAO_MOCK_FORK_POINT)))
  }

  val dummyBigInteger: BigInteger = BigInteger.ONE
  val negativeAmount: BigInteger = BigInteger.valueOf(-1)

  val invalidWeiAmount: BigInteger = new BigInteger("10000000001")
  val validWeiAmount: BigInteger = new BigInteger("10000000000")

  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val messageProcessor: McAddrOwnershipMsgProcessor = McAddrOwnershipMsgProcessor(mockNetworkParams)
  val contractAddress: Address = messageProcessor.contractAddress

  val scAddrStr1: String = "00C8F107a09cd4f463AFc2f1E6E5bF6022Ad4600"
  val scAddressObj1 = new Address("0x"+scAddrStr1)

  // signature 'i' is obtained signing the same scAddrStr1 string using the priv key corresponding to mc addr 'i'
  val mcAddrStr1: String = "ztZzTK8meAb3Be1XXN36Zu9NEAMCkYdiXXi"
  val mcSignatureStr1: String = "H3YWXUJbpgP1cwT4QuyAr9JSVehVPWpV841W0Zv/GTrffa1JfwHq34CjM1Y5TWWz82vtUyfdPh6K9jjJI2DKY8E="
  val mcAddrStr2: String = "ztfK1cpQtKmpwA9ELCZuE4ufQjS5rNx33D6"
  val mcSignatureStr2: String = "ILCkShV1GxwNeOr1p153T2ds38LtJVWtjEf5usCvfvEwWULUgpGbuikFoU/fC0wobG+2xVAHniQuxaxGjHn65cg="
  val mcAddrStr3: String = "ztes7TG8zHm2C1GzkMstmd8c6j6Q3pr8y5M"
  val mcSignatureStr3: String = "IKlZeexpu0l1uaa4y/Jv9okn93MsDZQQUcy97iuYZQN2f8vzgEV08SdfEcMUtoukERX72WSoDCBOSrp7d9fjQ68="
  val mcAddrStr4: String = "ztpXBsnFD9Ni1jvBPhfwswRSCoKG7irodXt"
  val mcSignatureStr4: String = "IG/74ayOEYDBsQLEFibVb1CbFFtWWKjCpwAFiVaaYb4wbPmimGytxJo0jf7d8UDQUf0TTdM+J5Qn1Mzm+k6WRNw="
  val mcAddrStr5: String = "ztVQPCqt17N3MQD9w8s736t2m26LHmzsJqG"
  val mcSignatureStr5: String = "IISbzxfiUUSDbp+KvQBrburAeF1QM4hEwJ0HLckL42QXTvEYLG0w3WnquyAiiNSUgv8kc+RAcShif+qbM8tXZ3g="

  val listOfMcAddrSign1 = new util.ArrayList[(String, String)]()
  listOfMcAddrSign1.add((mcAddrStr1, mcSignatureStr1))
  listOfMcAddrSign1.add((mcAddrStr2, mcSignatureStr2))
  listOfMcAddrSign1.add((mcAddrStr3, mcSignatureStr3))
  listOfMcAddrSign1.add((mcAddrStr4, mcSignatureStr4))
  listOfMcAddrSign1.add((mcAddrStr5, mcSignatureStr5))

  // a second sc address and its data
  val scAddrStr2: String = "cA12fcB886CBF73a39D87aAC9610f8a303536642"
  val scAddressObj2 = new Address("0x"+scAddrStr2)
  val mcAddrStr1_2: String = "ztkPjQddjaskk68LVvQTMxB6U5gGgsFCUzf"
  val mcSignatureStr1_2: String = "IKZJhxQtYGVTondsX0FHU+N1QjlxResb6md/HaEpGUirTqXUR8N4Bmi7p9szHTwpDKftq7QBRep8LUN4J8N3hz0="
  val mcAddrStr2_2: String = "ztVUwVnqCnWFANk9X6najvM1VBEJQcF4j8Y"
  val mcSignatureStr2_2: String = "H4IqujR8Wdh/3h5Wuf9GAb6dchn/8EjsDGPvIVtQD4XSLjNkwOGLLGKnCR896ZmH6t0nlQP2U/mksQOJaAMEH3g="

  val listOfMcAddrSign2 = new util.ArrayList[(String, String)]()
  listOfMcAddrSign2.add((mcAddrStr1_2, mcSignatureStr1_2))
  listOfMcAddrSign2.add((mcAddrStr2_2, mcSignatureStr2_2))

  // and here is the signature of the mcAddrStr1 made using the second sc address as msg. It is useful for negative tests
  val mcSignatureStr3_2: String = "IPkgJQzjSBdOKrlfOhihEm682GB8i+UmDTBMdLGaTCEkEK2cS1zOcPN+p9pdsTqSoiVXuprd+1kcuKHB2zvOlMI="

  val AddNewEventSig: Array[Byte] = getEventSignature("AddMcAddrOwnership(address,bytes3,bytes32)")
  val NumOfIndexedAddNewEvtParams = 1
  val RemoveEventSig: Array[Byte] = getEventSignature("RemoveMcAddrOwnership(address,bytes3,bytes32)")
  val NumOfIndexedRemoveEvtParams = 1

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

  @Test
  def testMethodIds(): Unit = {
    //The expected methodIds were calculated using this site: https://emn178.github.io/online-tools/keccak_256.html
    assertEquals("Wrong MethodId for AddNewOwnershipCmd", "579465dd", McAddrOwnershipMsgProcessor.AddNewOwnershipCmd)
    assertEquals("Wrong MethodId for RemoveOwnershipCmd", "9183c0da", McAddrOwnershipMsgProcessor.RemoveOwnershipCmd)
    assertEquals("Wrong MethodId for GetListOfAllOwnershipsCmd", "8ef05457", McAddrOwnershipMsgProcessor.GetListOfAllOwnershipsCmd)
    assertEquals("Wrong MethodId for GetListOfOwnershipsCmd", "169e2d15", McAddrOwnershipMsgProcessor.GetListOfOwnershipsCmd)
  }

  @Test
  def testInit(): Unit = {

    usingView(messageProcessor) { view =>

      assertTrue(McAddrOwnershipMsgProcessor.isForkActive(view.getConsensusEpochNumberAsInt))
      assertFalse(view.accountExists(contractAddress))
      assertFalse(McAddrOwnershipMsgProcessor.initDone(view))

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      assertTrue(view.accountExists(contractAddress))
      assertFalse(view.isEoaAccount(contractAddress))
      assertTrue(view.isSmartContractAccount(contractAddress))
      assertTrue(McAddrOwnershipMsgProcessor.initDone(view))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testInitBeforeFork(): Unit = {

    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(
      Option(intToConsensusEpochNumber(ZENDAO_MOCK_FORK_POINT-1)))

    usingView(messageProcessor) { view =>

      assertFalse(view.accountExists(contractAddress))
      assertFalse(McAddrOwnershipMsgProcessor.initDone(view))

      assertFalse(McAddrOwnershipMsgProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // assert no initialization took place
      assertFalse(view.accountExists(contractAddress))
      assertFalse(McAddrOwnershipMsgProcessor.initDone(view))
    }
  }


  @Test
  def testDoubleInit(): Unit = {

    usingView(messageProcessor) { view =>

      assertTrue(McAddrOwnershipMsgProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      assertFalse(view.accountExists(contractAddress))
      assertFalse(McAddrOwnershipMsgProcessor.initDone(view))

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      assertTrue(view.accountExists(contractAddress))
      assertTrue(McAddrOwnershipMsgProcessor.initDone(view))

      view.commit(bytesToVersion(getVersion.data()))

      val ex = intercept[MessageProcessorInitializationException] {
        messageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      }
      assertTrue(ex.getMessage.contains("already init"))
    }
  }


  @Test
  def testCanProcess(): Unit = {
    usingView(messageProcessor) { view =>

      // assert no initialization took place yet
      assertFalse(view.accountExists(contractAddress))
      assertFalse(McAddrOwnershipMsgProcessor.initDone(view))

      assertTrue(McAddrOwnershipMsgProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      // correct contract address
      assertTrue(messageProcessor.canProcess(getMessage(messageProcessor.contractAddress), view, view.getConsensusEpochNumberAsInt))

      // check initialization took place
      assertTrue(view.accountExists(contractAddress))
      assertTrue(view.isSmartContractAccount(contractAddress))
      assertFalse(view.isEoaAccount(contractAddress))

      // call a second time for checking it does not do init twice (would assert)
      assertTrue(messageProcessor.canProcess(getMessage(messageProcessor.contractAddress), view, view.getConsensusEpochNumberAsInt))

      // wrong address
      assertFalse(messageProcessor.canProcess(getMessage(randomAddress), view, view.getConsensusEpochNumberAsInt))
      // contract deployment: to == null
      assertFalse(messageProcessor.canProcess(getMessage(null), view, view.getConsensusEpochNumberAsInt))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testCanNotProcessBeforeFork(): Unit = {

    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(
      Option(intToConsensusEpochNumber(1)))

    usingView(messageProcessor) { view =>

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)
      val txHash1 = Keccak256.hash("tx")
      view.setupTxContext(txHash1, 10)
      createSenderAccount(view, initialAmount, scAddressObj1)
      val cmdInput = AddNewOwnershipCmdInput(mcAddrStr1, mcSignatureStr1)
      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )

      assertFalse(McAddrOwnershipMsgProcessor.isForkActive(view.getConsensusEpochNumberAsInt))

      // correct contract address and message but fork not yet reached
      assertFalse(messageProcessor.canProcess(msg, view, view.getConsensusEpochNumberAsInt))

      // the init did not take place
      assertFalse(view.accountExists(contractAddress))
      assertFalse(McAddrOwnershipMsgProcessor.initDone(view))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testAddAndRemoveOwnership(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      createSenderAccount(view, initialAmount, scAddressObj1)

      val cmdInput = AddNewOwnershipCmdInput(mcAddrStr1, mcSignatureStr1)

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      val expectedOwnershipId = Keccak256.hash(mcAddrStr1.getBytes(StandardCharsets.UTF_8))

      // positive case, verify we can add the data to view
      val returnData = assertGas(514937, msg, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData)
      println("This is the returned value: " + BytesUtils.toHexString(returnData))

      assertArrayEquals(expectedOwnershipId, returnData)

      // Checking log
      var listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      var expectedAddEvt = AddMcAddrOwnership(scAddressObj1, mcAddrStr1)
      checkAddNewOwnershipEvent(expectedAddEvt, listOfLogs(0))

      val txHash2 = Keccak256.hash("second tx")
      view.setupTxContext(txHash2, 10)
      // try processing a msg with the same data (same msg), should fail
      assertThrows[ExecutionRevertedException](withGas(messageProcessor.process(msg, view, _, defaultBlockContext)))

      // Checking that log doesn't change
      listOfLogs = view.getLogs(txHash2)
      assertEquals("Wrong number of logs", 0, listOfLogs.length)

      // add a second association
      val cmdInput2 = AddNewOwnershipCmdInput(mcAddrStr2, mcSignatureStr2)

      val data2: Array[Byte] = cmdInput2.encode()
      val msg2 = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data2,
        randomNonce,
        scAddressObj1)

      val txHash3 = Keccak256.hash("third tx")
      view.setupTxContext(txHash3, 10)

      val returnData2 = assertGas(382037, msg2, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData2)
      println("This is the returned value: " + BytesUtils.toHexString(returnData2))

      // Checking log
      listOfLogs = view.getLogs(txHash3)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      expectedAddEvt = AddMcAddrOwnership(scAddressObj1, mcAddrStr2)
      checkAddNewOwnershipEvent(expectedAddEvt, listOfLogs(0))

      // remove first ownership association
      val removeCmdInput1 = RemoveOwnershipCmdInput(Some(mcAddrStr1))
      val msg3 = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput1.encode(),
        randomNonce, scAddressObj1
      )

      val txHash4 = Keccak256.hash("forth tx")
      view.setupTxContext(txHash4, 10)

      val returnData3 = assertGas(30537, msg3, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData3)
      println("This is the returned value: " + BytesUtils.toHexString(returnData3))

      // Checking log
      listOfLogs = view.getLogs(txHash4)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedRemoveEvt = RemoveMcAddrOwnership(scAddressObj1, mcAddrStr1)
      checkRemoveNewOwnershipEvent(expectedRemoveEvt, listOfLogs(0))

       // get the list of ownerships via native smart contract interface, should return just the remaining one
      val expectedOwnershipData = McAddrOwnershipData(scAddrStr1, mcAddrStr2)
      val listOfExpectedOwnerships = new util.ArrayList[McAddrOwnershipData]
      listOfExpectedOwnerships.add(expectedOwnershipData)

      val msg4 = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(GetListOfAllOwnershipsCmd),
        randomNonce
      )
      val returnData4 = assertGas(19000, msg4, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData4)

      assertArrayEquals(McAddrOwnershipDataListEncoder.encode(listOfExpectedOwnerships), returnData4)

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessShortOpCode(): Unit = {
    usingView(messageProcessor) { view =>
      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      val args: Array[Byte] = new Array[Byte](0)
      val opCode = BytesUtils.fromHexString("ac")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid (1 byte instead of 4 bytes)
      val ex = intercept[ExecutionRevertedException] {
        assertGas(2100, msg, view, messageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Data length"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProcessInvalidOpCode(): Unit = {
    usingView(messageProcessor) { view =>
      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      val args: Array[Byte] = BytesUtils.fromHexString("1234567890")
      val opCode = BytesUtils.fromHexString("abadc0de")
      val msg = getDefaultMessage(opCode, args, randomNonce)

      // should fail because op code is invalid
      val ex = intercept[ExecutionRevertedException] {
        assertGas(2100, msg, view, messageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("op code not supported"))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testExtraBytesInGetListCmd(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      // try getting the list of objs with some extra byte after op code (should fail)
      val data: Array[Byte] = new Array[Byte](1)
      val msg = getDefaultMessage(
        BytesUtils.fromHexString(GetListOfAllOwnershipsCmd),
        data, randomNonce, value = BigInteger.ZERO)

      val ex = intercept[ExecutionRevertedException] {
        assertGas(2100, msg, view, messageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("invalid msg data length"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testGetListOfOwnerships(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount, scAddressObj1)

      val listOfAllExpectedData = new util.ArrayList[McAddrOwnershipData]()
      val listOfScAddress1ExpectedData = new util.ArrayList[McAddrOwnershipData]()

      // add some mc address associations for sc address 1
      for (i <- 0 until listOfMcAddrSign1.size()) {
        val mcAddr = listOfMcAddrSign1.get(i)._1
        val mcSignature = listOfMcAddrSign1.get(i)._2
        val cmdInput = AddNewOwnershipCmdInput(mcAddr, mcSignature)

        val data: Array[Byte] = cmdInput.encode()
        val msg = getMessage(contractAddress, BigInteger.ZERO,
          BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data, randomNonce, scAddressObj1)

        listOfAllExpectedData.add(McAddrOwnershipData(scAddrStr1.toLowerCase(), mcAddr))
        listOfScAddress1ExpectedData.add(McAddrOwnershipData(scAddrStr1.toLowerCase(), mcAddr))

        val returnData = withGas(messageProcessor.process(msg, view, _, defaultBlockContext))
        assertNotNull(returnData)
      }

      createSenderAccount(view, initialAmount, scAddressObj2)
      val listOfScAddress2ExpectedData = new util.ArrayList[McAddrOwnershipData]()

      // add some mc address associations for sc address 2
      for (i <- 0 until listOfMcAddrSign2.size()) {
        val mcAddr = listOfMcAddrSign2.get(i)._1
        val mcSignature = listOfMcAddrSign2.get(i)._2
        val cmdInput = AddNewOwnershipCmdInput(mcAddr, mcSignature)

        val data: Array[Byte] = cmdInput.encode()
        val msg = getMessage(contractAddress, BigInteger.ZERO,
          BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data, randomNonce, scAddressObj2)

        listOfAllExpectedData.add(McAddrOwnershipData(scAddrStr2.toLowerCase(), mcAddr))
        listOfScAddress2ExpectedData.add(McAddrOwnershipData(scAddrStr2.toLowerCase(), mcAddr))

        val returnData = withGas(messageProcessor.process(msg, view, _, defaultBlockContext))
        assertNotNull(returnData)
      }


      // get associations of each sc address
      val data1List = messageProcessor.getListOfMcAddrOwnerships(view, Some(scAddrStr1.toLowerCase()))
      val data2List = messageProcessor.getListOfMcAddrOwnerships(view, Some(scAddrStr2.toLowerCase()))

      // get all associations
      val dataAllList = messageProcessor.getListOfMcAddrOwnerships(view)

      assertEquals(listOfAllExpectedData, dataAllList.asJava)
      assertEquals(listOfScAddress1ExpectedData, data1List.asJava)
      assertEquals(listOfScAddress2ExpectedData, data2List.asJava)

      // get the list of all ownerships via native smart contract interface
      val returnData1 = getOwnershipList(view, scAddressObj1)
      assertNotNull(returnData1)
      assertArrayEquals(McAddrOwnershipDataListEncoder.encode(listOfScAddress1ExpectedData), returnData1)

      val returnData2 = getOwnershipList(view, scAddressObj2)
      assertNotNull(returnData2)
      assertArrayEquals(McAddrOwnershipDataListEncoder.encode(listOfScAddress2ExpectedData), returnData2)

      val returnAllData = getAllOwnershipList(view)
      assertNotNull(returnAllData)
      assertArrayEquals(McAddrOwnershipDataListEncoder.encode(listOfAllExpectedData), returnAllData)

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testProviderMethodsWithoutInit(): Unit = {
    // check it is safe call these methods even if we did not init the smart contract
    usingView(messageProcessor) { view =>
      val listResult = messageProcessor.getListOfMcAddrOwnerships(view, Some(scAddrStr1.toLowerCase()))
      assertTrue(listResult.isEmpty)
      val boolRes = messageProcessor.ownershipDataExist(view, new Array[Byte](32))
      assertFalse(boolRes)
    }
  }

  @Test
  def testOwnershipLinkedList(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount, scAddressObj1)

      val listOfExpectedData = new util.ArrayList[McAddrOwnershipData]()
      val numOfOwnerships = listOfMcAddrSign1.size()
      assertTrue("Test data list too short", numOfOwnerships > 3)

      // add some mc sc address associations
      for (i <- 0 until numOfOwnerships) {
        val mcAddr = listOfMcAddrSign1.get(i)._1
        val mcSignature = listOfMcAddrSign1.get(i)._2
        val cmdInput = AddNewOwnershipCmdInput(mcAddr, mcSignature)

        val data: Array[Byte] = cmdInput.encode()
        val msg = getMessage(contractAddress, BigInteger.ZERO,
          BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data, randomNonce, scAddressObj1)

        listOfExpectedData.add(McAddrOwnershipData(scAddrStr1, mcAddr))

        val returnData = withGas(messageProcessor.process(msg, view, _, defaultBlockContext))
        assertNotNull(returnData)
      }

      val ownershipList = getAllOwnershipList(view)
      val ownershipList2 = getOwnershipList(view, scAddressObj1)

      //Check getListOfForgers
      val expectedListData = McAddrOwnershipDataListEncoder.encode(listOfExpectedData)
      assertArrayEquals(expectedListData, ownershipList)
      assertArrayEquals(expectedListData, ownershipList2)

      // remove in the middle of the list
      checkRemoveItemFromList(view, listOfExpectedData, 2)

      // remove at the beginning of the list (head)
      checkRemoveItemFromList(view, listOfExpectedData, 0)

      // remove at the end of the list
      checkRemoveItemFromList(view, listOfExpectedData, listOfExpectedData.size()-1)

      // remove the last element we have
      checkRemoveItemFromList(view, listOfExpectedData, 0)

      // call msg processor for removing a not existing association. In this casethe linked list is not even accessed
      val ex = intercept[ExecutionRevertedException] {
        removeOwnership(view, new Address("0x"+scAddrStr1), mcAddrStr1_2)
      }
      assertTrue(ex.getMessage.contains("does not exist"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testInvalidAddNewOwnershipCmd(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount, scAddressObj1)

      val cmdInput = AddNewOwnershipCmdInput(mcAddrStr1, mcSignatureStr1)
      val data: Array[Byte] = cmdInput.encode()

      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )

      val returnData = assertGas(514937, msg, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData)


      // try adding with a value amount not null
      var msgBad = getMessage(contractAddress, BigInteger.ONE.negate(),
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ cmdInput.encode(),
        randomNonce, scAddressObj1
      )
      var ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Value must be zero"))

      // try processing a msg with a trailing byte in the arguments, should fail
      val badData = Bytes.concat(data, new Array[Byte](1))
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ badData,
        randomNonce,
        scAddressObj1)

      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // try processing a msg with different data (different nonce) but same ownership, should fail
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1)

      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("already associated"))

      // try using a wrong sender
      createSenderAccount(view, initialAmount, origin)
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data,
        randomNonce,
        origin)
      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Invalid mc signature"))

      // try using a wrong signature
      var cmdInputBad = AddNewOwnershipCmdInput(mcAddrStr2, mcSignatureStr1)
      var dataBad: Array[Byte] = cmdInputBad.encode()

      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ dataBad,
        randomNonce,
        scAddressObj1)
      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Invalid mc signature"))

      // try using a wrong signature
      cmdInputBad = AddNewOwnershipCmdInput(mcAddrStr2, mcSignatureStr1)
      dataBad = cmdInputBad.encode()

      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ dataBad,
        randomNonce,
        scAddressObj1)
      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Invalid mc signature"))

      // try using an ill formed mc addr (we take a shorter BTC address)
      val btcAddr = "1BNwxHGaFbeUBitpjy2AsKpJ29Ybxntqvb"
      val ex2 = intercept[IllegalArgumentException] {

        cmdInputBad = AddNewOwnershipCmdInput(btcAddr, mcSignatureStr1)

        dataBad = cmdInputBad.encode()
      }
      assertTrue(ex2.getMessage.contains("Invalid mc address length"))

      // try adding a new association using a mc address already associated to a sc address, should fail
      createSenderAccount(view, initialAmount, scAddressObj2)

      val cmdInput2 = AddNewOwnershipCmdInput(mcAddrStr1, mcSignatureStr3_2)
      val data2: Array[Byte] = cmdInput2.encode()

      val msg2 = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data2,
        randomNonce,
        scAddressObj2
      )

      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msg2, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains(s"already associated to sc address ${scAddrStr1.toLowerCase()}"))
    }
  }

  @Test
  def testInvalidRemoveOwnershipCmd(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount, scAddressObj1)

      val cmdInput = AddNewOwnershipCmdInput(mcAddrStr1, mcSignatureStr1)

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )

      val returnData = assertGas(514937, msg, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData)

      val removeCmdInput = RemoveOwnershipCmdInput(Some(mcAddrStr1))

      // try removing with a value amount not null (value 1)
      var msgBad = getMessage(contractAddress, BigInteger.ONE,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode(),
        randomNonce, scAddressObj1
      )
      var ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Value must be zero"))

      // try removing with a value amount not null (value -1)
      msgBad = getMessage(contractAddress, BigInteger.ONE.negate(),
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode(),
        randomNonce, scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Value must be zero"))

      // should fail because input data has a trailing byte
      val badData = new Array[Byte](1)
      msgBad = getMessage(contractAddress, BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode() ++ badData,
        randomNonce, scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // should fail because sender does not exist
      msgBad = getMessage(contractAddress, BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode(),
        randomNonce, origin
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      assertTrue(ex.getMessage.contains("account does not exist"))

      // should fail because sender is not the owner
      createSenderAccount(view, initialAmount, origin)
      msgBad = getMessage(contractAddress, BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode(),
        randomNonce, origin
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(messageProcessor.process(msgBad, view, _, defaultBlockContext))
      }
      val ownershipIdStr = BytesUtils.toHexString(getOwnershipId(mcAddrStr1))
      assertTrue(ex.getMessage.contains("is not the owner"))

      // should fail because mc address in not legal (we take a shorter BTC address)
      val btcAddr = "1BNwxHGaFbeUBitpjy2AsKpJ29Ybxntqvb"
      val removeCmdInputBad = RemoveOwnershipCmdInput(Some(btcAddr))
      val ex2 = intercept[IllegalArgumentException] {
        msgBad = getMessage(contractAddress, BigInteger.ZERO,
          BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInputBad.encode(),
          randomNonce, scAddressObj1
        )
      }
      assertTrue(ex2.getMessage.contains("Invalid mc address length"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  def checkRemoveItemFromList(stateView: AccountStateView, inputList: java.util.List[McAddrOwnershipData],
                              itemPosition: Int): Unit = {

    // get the info related to the item to remove
    val info = inputList.remove(itemPosition)

    // call msg processor for removing the selected obj
    removeOwnership(stateView, new Address("0x"+info.scAddress), info.mcTransparentAddress)

    // call msg processor for retrieving the resulting list of forgers
    val returnedList = getAllOwnershipList(stateView)

    // check the results:
    //  we removed just one element
    val inputListData = McAddrOwnershipDataListEncoder.encode(inputList)
    assertArrayEquals(inputListData, returnedList)
  }

  def checkAddNewOwnershipEvent(expectedEvent: AddMcAddrOwnership, actualEvent: EthereumConsensusDataLog): Unit = {
    assertEquals("Wrong address", contractAddress, actualEvent.address)
    assertEquals("Wrong number of topics", NumOfIndexedAddNewEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", AddNewEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong from address in topic", expectedEvent.scAddress, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.scAddress.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(
      TypeReference.makeTypeReference(expectedEvent.mcAddress_3.getTypeAsString),
    TypeReference.makeTypeReference(expectedEvent.mcAddress_32.getTypeAsString))
      .asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong bytes in first part of mc address", expectedEvent.mcAddress_3, listOfDecodedData.get(0))
    assertEquals("Wrong bytes in second part of mc address", expectedEvent.mcAddress_32, listOfDecodedData.get(1))
  }

  def checkRemoveNewOwnershipEvent(expectedEvent: RemoveMcAddrOwnership, actualEvent: EthereumConsensusDataLog) : Unit = {
    assertEquals("Wrong address", contractAddress, actualEvent.address)
    assertEquals("Wrong number of topics", NumOfIndexedRemoveEvtParams + 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", RemoveEventSig, actualEvent.topics(0).toBytes)
    assertEquals("Wrong from address in topic", expectedEvent.scAddress, decodeEventTopic(actualEvent.topics(1), TypeReference.makeTypeReference(expectedEvent.scAddress.getTypeAsString)))

    val listOfRefs = util.Arrays.asList(
      TypeReference.makeTypeReference(expectedEvent.mcAddress_3.getTypeAsString),
      TypeReference.makeTypeReference(expectedEvent.mcAddress_32.getTypeAsString))
      .asInstanceOf[util.List[TypeReference[Type[_]]]]
    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong bytes in first part of mc address", expectedEvent.mcAddress_3, listOfDecodedData.get(0))
    assertEquals("Wrong bytes in second part of mc address", expectedEvent.mcAddress_32, listOfDecodedData.get(1))
  }

  def removeOwnership(stateView: AccountStateView, scAddress: Address, mcTransparentAddress: String): Unit = {
    val nonce = randomNonce

    // create command arguments
    val removeCmdInput = RemoveOwnershipCmdInput(Some(mcTransparentAddress))
    val data: Array[Byte] = removeCmdInput.encode()
    val msg = getMessage(
      contractAddress,
      0,
      BytesUtils.fromHexString(RemoveOwnershipCmd) ++ data,
      nonce,
      scAddress
    )

    // try processing the removal of ownership, should succeed
    val returnData = withGas(messageProcessor.process(msg, stateView, _, defaultBlockContext))
    assertNotNull(returnData)
    assertArrayEquals(getOwnershipId(mcTransparentAddress), returnData)
  }

  def getAllOwnershipList(stateView: AccountStateView): Array[Byte] = {
    val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfAllOwnershipsCmd), randomNonce)
    stateView.setupAccessList(msg)

    val (returnData, usedGas) = withGas { gas =>
      val result = messageProcessor.process(msg, stateView, gas, defaultBlockContext)
      (result, gas.getUsedGas)
    }
    // gas consumption depends on the number of items in the list
    assertTrue(usedGas.compareTo(0) > 0)
    assertTrue(usedGas.compareTo(500000) < 0)

    assertNotNull(returnData)
    returnData
  }

  def getOwnershipList(stateView: AccountStateView, scAddress: Address): Array[Byte] = {
    val getCmdInput = GetOwnershipsCmdInput(scAddress)
    val data: Array[Byte] = getCmdInput.encode()

    val msg = getMessage(
      contractAddress, 0,
      BytesUtils.fromHexString(GetListOfOwnershipsCmd) ++ data, randomNonce)
    stateView.setupAccessList(msg)

    val (returnData, usedGas) = withGas { gas =>
      val result = messageProcessor.process(msg, stateView, gas, defaultBlockContext)
      (result, gas.getUsedGas)
    }
    // gas consumption depends on the number of items in the list
    assertTrue(usedGas.compareTo(0) > 0)
    assertTrue(usedGas.compareTo(500000) < 0)

    assertNotNull(returnData)
    returnData
  }

  @Test
  def testSimple(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount, scAddressObj1)

      val listOfExpectedData = new util.ArrayList[McAddrOwnershipData]()
      val numOfOwnerships = 1

      for (i <- 0 until numOfOwnerships) {
        val mcAddr = listOfMcAddrSign1.get(i)._1
        val mcSignature = listOfMcAddrSign1.get(i)._2
        val cmdInput = AddNewOwnershipCmdInput(mcAddr, mcSignature)

        val data: Array[Byte] = cmdInput.encode()
        val msg = getMessage(contractAddress, BigInteger.ZERO,
          BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data, randomNonce, scAddressObj1)

        listOfExpectedData.add(McAddrOwnershipData(scAddrStr1, mcAddr))

        val returnData = withGas(messageProcessor.process(msg, view, _, defaultBlockContext))
        assertNotNull(returnData)
      }

      val ownershipList = getAllOwnershipList(view)

      val expectedListData = McAddrOwnershipDataListEncoder.encode(listOfExpectedData)
      assertArrayEquals(expectedListData, ownershipList)

      val ownershipList2 = getOwnershipList(view, scAddressObj1)
      assertArrayEquals(expectedListData, ownershipList2)


      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testSimple2(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount, scAddressObj1)
      createSenderAccount(view, initialAmount, scAddressObj2)

      val listOfScAddress2ExpectedData = new util.ArrayList[McAddrOwnershipData]()
      val numOfOwnerships2 = 1

      // add some mc address associations for sc address 2
      for (i <- 0 until numOfOwnerships2) {
        val mcAddr = listOfMcAddrSign2.get(i)._1
        val mcSignature = listOfMcAddrSign2.get(i)._2
        val cmdInput = AddNewOwnershipCmdInput(mcAddr, mcSignature)

        val data: Array[Byte] = cmdInput.encode()
        val msg = getMessage(contractAddress, BigInteger.ZERO,
          BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data, randomNonce, scAddressObj2)

        listOfScAddress2ExpectedData.add(McAddrOwnershipData(scAddrStr2.toLowerCase(), mcAddr))

        val returnData = withGas(messageProcessor.process(msg, view, _, defaultBlockContext))
        assertNotNull(returnData)
      }

      val listOfExpectedData = new util.ArrayList[McAddrOwnershipData]()
      val numOfOwnerships1 = 2

      for (i <- 0 until numOfOwnerships1) {
        val mcAddr = listOfMcAddrSign1.get(i)._1
        val mcSignature = listOfMcAddrSign1.get(i)._2
        val cmdInput = AddNewOwnershipCmdInput(mcAddr, mcSignature)

        val data: Array[Byte] = cmdInput.encode()
        val msg = getMessage(contractAddress, BigInteger.ZERO,
          BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data, randomNonce, scAddressObj1)

        listOfExpectedData.add(McAddrOwnershipData(scAddrStr1, mcAddr))

        val returnData = withGas(messageProcessor.process(msg, view, _, defaultBlockContext))
        assertNotNull(returnData)
      }

      val ownershipList = getAllOwnershipList(view)

      val expectedListData = McAddrOwnershipDataListEncoder.encode(listOfExpectedData)
      //assertArrayEquals(expectedListData, ownershipList)

      val ownershipList2 = getOwnershipList(view, scAddressObj1)
      assertArrayEquals(expectedListData, ownershipList2)


      view.commit(bytesToVersion(getVersion.data()))
    }
  }

}
