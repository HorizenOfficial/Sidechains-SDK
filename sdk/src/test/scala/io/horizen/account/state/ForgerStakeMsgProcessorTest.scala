package io.horizen.account.state


import com.google.common.primitives.Bytes
import io.horizen.account.abi.{ABIDecoder, MsgProcessorInputDecoder}
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.fork.{Version1_2_0Fork, Version1_3_0Fork, Version1_4_0Fork}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import io.horizen.account.state.ForgerStakeMsgProcessor._
import io.horizen.account.state.ForgerStakeStorage.getStorageVersionFromDb
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state.events.{DelegateForgerStake, OpenForgerList, StakeUpgrade, WithdrawForgerStake}
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.utils.{EthereumTransactionDecoder, WellKnownAddresses, ZenWeiConverter}
import io.horizen.evm.{Address, Hash}
import io.horizen.fixtures.StoreFixture
import io.horizen.fork.{ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch, SimpleForkConfigurator}
import io.horizen.params.NetworkParams
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.secret.PrivateKey25519
import io.horizen.utils.{BytesUtils, Ed25519, Pair}
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.generated.{Int32, Uint256}
import org.web3j.abi.datatypes.{DynamicArray, Type}
import org.web3j.abi.{DefaultFunctionReturnDecoder, FunctionReturnDecoder, TypeReference}
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256
import sparkz.util.serialization.VLQByteBufferReader

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util
import java.util.{Optional, Random}
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

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
  val StakeUpgradeEventSig: Array[Byte] = getEventSignature("StakeUpgrade(uint32,uint32)")
  val DisableEventSig: Array[Byte] = getEventSignature("DisableStakeV1()")

  val V1_2_MOCK_FORK_POINT: Int = 100
  val V1_3_MOCK_FORK_POINT: Int = 200
  val V1_4_MOCK_FORK_POINT: Int = 300

  val blockContextForkV1_3 = new BlockContext(
    Address.ZERO,
    0,
    0,
    DefaultGasFeeFork.blockGasLimit,
    0,
    V1_3_MOCK_FORK_POINT,
    0,
    1,
    MockedHistoryBlockHashProvider,
    Hash.ZERO
  )

  val blockContextForkV1_4 = new BlockContext(
    Address.ZERO,
    0,
    0,
    DefaultGasFeeFork.blockGasLimit,
    0,
    V1_4_MOCK_FORK_POINT,
    0,
    1,
    MockedHistoryBlockHashProvider,
    Hash.ZERO
  )

  @Before
  def setUp(): Unit = {
    val forkConfigurator = new SimpleForkConfigurator() {
      override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = List(
        new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](
          SidechainForkConsensusEpoch(V1_2_MOCK_FORK_POINT, V1_2_MOCK_FORK_POINT, V1_2_MOCK_FORK_POINT),
          new Version1_2_0Fork(true)
        ),
        new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](
          SidechainForkConsensusEpoch(V1_3_MOCK_FORK_POINT, V1_3_MOCK_FORK_POINT, V1_3_MOCK_FORK_POINT),
          new Version1_3_0Fork(true)
        ),
        new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](
          SidechainForkConsensusEpoch(V1_4_MOCK_FORK_POINT, V1_4_MOCK_FORK_POINT, V1_4_MOCK_FORK_POINT),
          new Version1_4_0Fork(true)
        )
      ).asJava
    }
    ForkManagerUtil.initializeForkManager(forkConfigurator, "regtest")
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
    //OpenStakeForgerListCmd signature is wrong, it misses a closing parenthesis. The correct signature required an hard
    // fork, enabled with version 1.2. For backward compatibility the wrong signature can still be used.
    assertEquals("Wrong MethodId for OpenStakeForgerListCmd", "b05bf06c", ForgerStakeMsgProcessor.OpenStakeForgerListCmd)
    assertEquals("Wrong MethodId for OpenStakeForgerListCmdCorrect", "06f12075", ForgerStakeMsgProcessor.OpenStakeForgerListCmdCorrect)

    assertEquals("Wrong MethodId for StakeOfCmd", "42623360", ForgerStakeMsgProcessor.StakeOfCmd)
    assertEquals("Wrong MethodId for GetPagedListOfForgersCmd", "af5f63ef", ForgerStakeMsgProcessor.GetPagedListOfForgersCmd)
    assertEquals("Wrong MethodId for GetPagedForgersStakesOfUserCmd", "5f6dfc1d", ForgerStakeMsgProcessor.GetPagedForgersStakesOfUserCmd)
    assertEquals("Wrong MethodId for UpgradeCmd", "d55ec697", ForgerStakeMsgProcessor.UpgradeCmd)
    assertEquals("Wrong MethodId for DisableCmd", "2f2770db", ForgerStakeMsgProcessor.DisableCmd)
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

      // test nothing changed after Fork 1_3 and new storage model

      // correct contract address
      assertTrue(TestContext.canProcess(forgerStakeMessageProcessor, getMessage(forgerStakeMessageProcessor.contractAddress), view, V1_3_MOCK_FORK_POINT))
      // wrong address
      assertFalse(TestContext.canProcess(forgerStakeMessageProcessor, getMessage(randomAddress), view, V1_3_MOCK_FORK_POINT))
      // contact deployment: to == null
      assertFalse(TestContext.canProcess(forgerStakeMessageProcessor, getMessage(null), view, V1_3_MOCK_FORK_POINT))

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

      // assert we have opened the forger list
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

      // assert we have opened the forger list
      isOpen = forgerStakeMessageProcessor.isForgerListOpen(view)
      assertTrue(isOpen)
    }
  }


  @Test
  def testOpenStakeForgerListCorrectSignature(): Unit = {

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

      // Test with the correct signature before fork. It should fail.
      var msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmdCorrect) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      // should fail because before fork OpenStakeForgerListCmdCorrect is not a valid function signature
      val exc = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertEquals(s"op code not supported: $OpenStakeForgerListCmdCorrect", exc.getMessage)

      // Test after fork. It should work.
      val blockContext = new BlockContext(
        Address.ZERO,
        0,
        0,
        DefaultGasFeeFork.blockGasLimit,
        0,
        V1_2_MOCK_FORK_POINT,
        0,
        1,
        MockedHistoryBlockHashProvider,
        Hash.ZERO
      )


      var returnData = assertGas(45937, msg, view, forgerStakeMessageProcessor, blockContext)
      assertArrayEquals(Array[Byte](1, 0, 0), returnData)

      // Checking log
      val listOfLogs = view.getLogs(txHash1.asInstanceOf[Array[Byte]])
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedAddStakeEvt = OpenForgerList(forgerIndex, msg.getFrom, blockSignerProposition1)
      checkOpenForgerStakeListEvent(expectedAddStakeEvt, listOfLogs(0))

      val isOpen = forgerStakeMessageProcessor.isForgerListOpen(view)
      assertFalse(isOpen)

      nonce = 1
      forgerIndex = 1
      msgToSign = ForgerStakeMsgProcessor.getOpenStakeForgerListCmdMessageToSign(
        forgerIndex, ownerAddressProposition.address(), nonce.toByteArray)

      signature = blockSignSecret2.sign(msgToSign)
      cmdInput = OpenStakeForgerListCmdInput(
        forgerIndex, signature
      )

      // Test the old signature after fork. It should still work.

      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(OpenStakeForgerListCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      returnData = assertGas(6237, msg, view, forgerStakeMessageProcessor, blockContext)
      assertArrayEquals(Array[Byte](1, 1, 0), returnData)


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
//      val returnData = assertGas(139712, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
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
//      val returnData2 = assertGas(119812, msg2, view, forgerStakeMessageProcessor, defaultBlockContext)
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
//      val returnData3 = assertGas(28481, msg3, view, forgerStakeMessageProcessor, defaultBlockContext)
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
//      val returnData4 = assertGas(12600, msg4, view, forgerStakeMessageProcessor, defaultBlockContext)
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
      var ex = intercept[ExecutionRevertedException] {
        assertGas(4800, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Forger is not in the allowed list"))

      // test with new storage model

      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))

      // should fail because forger is not in the allowed list
      ex = intercept[ExecutionRevertedException] {
        assertGas(9000, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
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
      var ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("Data length"))

      // test with new storage model
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))
      ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
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
      var ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("op code not supported"))

      // Same test after Forger Stake Storage activation
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))

      ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
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

      // test with new storage model
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))

      assertThrows[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
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

      // test with new storage model
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))
      assertThrows[ExecutionRevertedException] {
        assertGas(4400, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
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

      var ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, defaultBlockContext)
      }
      assertTrue(ex.getMessage.contains("invalid msg data length"))

      // test with new storage model
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))
      ex = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
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
      val forgerList = forgerStakeMessageProcessor.getListOfForgersStakes(view, false)
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
    // The tx has a signature r value whose byte array representation is 31 bytes long (not 32), and this should be supported by the ABI encoding (no exceptions)
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
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testUpgradeBase(): Unit = {

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(ZenWeiConverter.MAX_MONEY_IN_WEI)
      createSenderAccount(view, initialAmount)

      assertEquals(ForgerStakeStorageVersion.VERSION_1, getStorageVersionFromDb(view))


      val nonce = 0
      // Test upgrade before reaching the fork point. It should fail.
      var msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), nonce, ownerAddressProposition.address())

      // should fail because, before Version 1.3 fork, UpgradeCmd is not a valid function signature
      val blockContextBeforeFork = new BlockContext(
        Address.ZERO,
        0,
        0,
        DefaultGasFeeFork.blockGasLimit,
        0,
        V1_3_MOCK_FORK_POINT - 1,
        0,
        1,
        MockedHistoryBlockHashProvider,
        Hash.ZERO
      )

      var exc = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextBeforeFork)
      }
      assertEquals(s"op code not supported: $UpgradeCmd", exc.getMessage)
      assertEquals(ForgerStakeStorageVersion.VERSION_1, getStorageVersionFromDb(view))

      // Test after fork.

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      val version = new BigInteger(1, returnData).intValueExact()
      assertEquals(ForgerStakeStorageVersion.VERSION_2.id, version)
      assertEquals(ForgerStakeStorageVersion.VERSION_2, getStorageVersionFromDb(view))

      // Checking log
      val listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedEvent = StakeUpgrade(ForgerStakeStorageVersion.VERSION_1.id, ForgerStakeStorageVersion.VERSION_2.id)
      checkUpgradeStakeEvent(expectedEvent, listOfLogs(0))

      // Check that it cannot be called twice
      exc = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"Forger stake storage already upgraded", exc.getMessage)

      // Check that it is not payable
      val value = validWeiAmount
      msg = getMessage(
        contractAddress, value, BytesUtils.fromHexString(UpgradeCmd), nonce, ownerAddressProposition.address())

      val excPayable = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"Call value must be zero", excPayable.getMessage)

      // try processing a msg with a trailing byte in the arguments
      val badData = new Array[Byte](1)
      val msgBad = getMessage(contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd) ++ badData, randomNonce)

      // should fail because input has a trailing byte
      exc = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msgBad, view, blockContextForkV1_3, _))
      }
      assertTrue(exc.getMessage.contains("invalid msg data length"))

    }
  }

  @Test
  def testForgerStakeStorageV2(): Unit = {

    val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

    val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("aa22334455667788aa2233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("33b775fd4c1117446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10000).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition1, vrfPublicKey1),
                                                                        (blockSignerProposition2, vrfPublicKey2)))

      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))

      // Check that at the beginning there is no stake
      val getAllStakesMsg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)
      var getAllStakesReturnData = assertGas(4200, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      var listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)

      // Add the first stake
      //Setting the context, it is used for retrieving the events
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val addCmdInput = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
        ownerAddressProposition.address()
      )

      val data: Array[Byte] = addCmdInput.encode()
      val addStakeMsg1 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)

      val expectedStake1 = AccountForgingStakeInfo(forgerStakeMessageProcessor.getStakeId(addStakeMsg1),
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
          ownerAddressProposition, validWeiAmount))


      val returnData = assertGas(208312, addStakeMsg1, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData)
      assertArrayEquals(expectedStake1.stakeId, returnData)

      // verify we added the amount to smart contract and we charge the sender
      var totalStakedValue = expectedStake1.forgerStakeData.stakedAmount
      var expOriginBalance = initialAmount.subtract(expectedStake1.forgerStakeData.stakedAmount)
      assertEquals(totalStakedValue, view.getBalance(contractAddress))
      assertEquals(expOriginBalance, view.getBalance(origin))

      // Checking log
      var listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      var expectedAddStakeEvt = DelegateForgerStake(addStakeMsg1.getFrom, ownerAddressProposition.address(), expectedStake1.stakeId, addStakeMsg1.getValue)
      checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

      getAllStakesReturnData = assertGas(14700, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      listOfExpectedForgerStakes.add(expectedStake1)
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)
      checkForgerStakeStorageV2Consistency(view, expectedStake1.stakeId)

      // try processing a msg with the same stake (same msg), should fail
      val txHash2 = Keccak256.hash("second tx")
      view.setupTxContext(txHash2, 10)

      assertThrows[ExecutionRevertedException](
        withGas(TestContext.process(forgerStakeMessageProcessor, addStakeMsg1, view, blockContextForkV1_3, _))
      )

      // Checking that log doesn't change
      listOfLogs = view.getLogs(txHash2)
      assertEquals("Wrong number of logs", 0, listOfLogs.length)

      // try processing a msg with a trailing byte in the arguments
      val badData = Bytes.concat(data, new Array[Byte](1))
      val msgBad = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ badData, randomNonce)

      // should fail because input has a trailing byte
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msgBad, view, blockContextForkV1_3, _))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // Add a second stake: try processing a msg with same input but different stake id (different nonce), should succeed
      val txHash3 = Keccak256.hash("third tx")
      view.setupTxContext(txHash3, 10)
      val addStakeMsg2 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)

      val expectedStake2 = AccountForgingStakeInfo(forgerStakeMessageProcessor.getStakeId(addStakeMsg2),
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
          ownerAddressProposition, validWeiAmount))

      val returnData2 = assertGas(148612, addStakeMsg2, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData2)
      assertArrayEquals(expectedStake2.stakeId, returnData2)

      getAllStakesReturnData = assertGas(25200, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      listOfExpectedForgerStakes.add(expectedStake2)
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)
      checkForgerStakeStorageV2Consistency(view, expectedStake1.stakeId)
      checkForgerStakeStorageV2Consistency(view, expectedStake2.stakeId)

      totalStakedValue = totalStakedValue.add(expectedStake2.forgerStakeData.stakedAmount)
      expOriginBalance = expOriginBalance.subtract(expectedStake2.forgerStakeData.stakedAmount)
      assertEquals(totalStakedValue, view.getBalance(contractAddress))
      assertEquals(expOriginBalance, view.getBalance(origin))

      // Add a new stake with a different owner and forger
      val privateKey2: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("anotherrandomkey".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition2: AddressProposition = privateKey2.publicImage()

      val txHash4 = Keccak256.hash("forth tx")
      view.setupTxContext(txHash4, 10)

      val addCmdInput2 = AddNewStakeCmdInput(
        ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2),
        ownerAddressProposition2.address()
      )

      val addStakeMsg3 = getMessage(contractAddress, validWeiAmount,
                        BytesUtils.fromHexString(AddNewStakeCmd) ++ addCmdInput2.encode(), randomNonce)

      val expectedStake3 = AccountForgingStakeInfo(forgerStakeMessageProcessor.getStakeId(addStakeMsg3),
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2),
          ownerAddressProposition2, validWeiAmount))

      val returnData3 = assertGas(188412, addStakeMsg3, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData3)
      assertArrayEquals(expectedStake3.stakeId, returnData3)

      // verify we added the amount to smart contract and we charge the sender
      totalStakedValue = totalStakedValue.add(expectedStake3.forgerStakeData.stakedAmount)
      expOriginBalance = expOriginBalance.subtract(expectedStake3.forgerStakeData.stakedAmount)
      assertEquals(totalStakedValue, view.getBalance(contractAddress))
      assertEquals(expOriginBalance, view.getBalance(origin))


      // Checking log
      listOfLogs = view.getLogs(txHash4)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      expectedAddStakeEvt = DelegateForgerStake(addStakeMsg3.getFrom, ownerAddressProposition2.address(), expectedStake3.stakeId, addStakeMsg3.getValue)
      checkAddNewForgerStakeEvent(expectedAddStakeEvt, listOfLogs(0))

      getAllStakesReturnData = assertGas(35700, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      listOfExpectedForgerStakes.add(expectedStake1)
      listOfExpectedForgerStakes.add(expectedStake2)
      listOfExpectedForgerStakes.add(expectedStake3)
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)
      checkForgerStakeStorageV2Consistency(view, expectedStake1.stakeId)
      checkForgerStakeStorageV2Consistency(view, expectedStake2.stakeId)
      checkForgerStakeStorageV2Consistency(view, expectedStake3.stakeId)

      // remove first stake id
      val nonce3 = randomNonce
      val msgToSign = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(expectedStake1.stakeId, origin, nonce3.toByteArray)
      val msgSignature = privateKey.sign(msgToSign)

      // create command arguments
      val removeCmdInput = RemoveStakeCmdInput(expectedStake1.stakeId, msgSignature)

      val msg5 = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput.encode(), nonce3)

      val txHash5 = Keccak256.hash("fifth tx")
      view.setupTxContext(txHash5, 10)
      // try processing the removal of stake, should succeed
      val returnData5 = assertGas(48481, msg5, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData5)
      assertArrayEquals(expectedStake1.stakeId, returnData5)

      // verify we removed the amount from smart contract and we added it to owner (sender is not concerned)

      var currentTotalStakedValue = totalStakedValue.subtract(expectedStake1.forgerStakeData.stakedAmount)
      assertEquals(currentTotalStakedValue, view.getBalance(contractAddress))
      assertEquals(expOriginBalance, view.getBalance(origin))

      assertEquals(expectedStake1.forgerStakeData.stakedAmount, view.getBalance(ownerAddressProposition.address()))
      assertEquals(BigInteger.ZERO, view.getBalance(ownerAddressProposition2.address()))

      // Checking log
      listOfLogs = view.getLogs(txHash5)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedRemoveStakeEvent = WithdrawForgerStake(ownerAddressProposition.address(), expectedStake1.stakeId)
      checkRemoveForgerStakeEvent(expectedRemoveStakeEvent, listOfLogs(0))

      //Checking list of stakes
      getAllStakesReturnData = assertGas(25200, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      listOfExpectedForgerStakes.add(expectedStake3)
      listOfExpectedForgerStakes.add(expectedStake2)
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)
      checkForgerStakeStorageV2Consistency(view, expectedStake3.stakeId)
      checkForgerStakeStorageV2Consistency(view, expectedStake2.stakeId)

      // Add again another stake
      val txHash11 = Keccak256.hash("first1 tx")
      view.setupTxContext(txHash11, 10)

      val addStakeMsg4 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd) ++ data, randomNonce)

      val expectedStake4 = AccountForgingStakeInfo(forgerStakeMessageProcessor.getStakeId(addStakeMsg4),
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1),
          ownerAddressProposition, validWeiAmount))

      val returnData11 = assertGas(148612, addStakeMsg4, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData11)
      assertArrayEquals(expectedStake4.stakeId, returnData11)

      //Checking list of stakes
      getAllStakesReturnData = assertGas(35700, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      listOfExpectedForgerStakes.add(expectedStake3)
      listOfExpectedForgerStakes.add(expectedStake2)
      listOfExpectedForgerStakes.add(expectedStake4)
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)
      checkForgerStakeStorageV2Consistency(view, expectedStake3.stakeId)
      checkForgerStakeStorageV2Consistency(view, expectedStake2.stakeId)
      checkForgerStakeStorageV2Consistency(view, expectedStake4.stakeId)
      assertArrayEquals(NULL_HEX_STRING_32, ForgerStakeStorageV2.forgerStakeArray.getValue(view, 3))

      expOriginBalance = expOriginBalance.subtract(expectedStake4.forgerStakeData.stakedAmount)

      // remove second stake id
      val nonce4 = randomNonce

      val msgToSign_2 = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(expectedStake2.stakeId, origin, nonce4.toByteArray)
      val msgSignature_2 = privateKey.sign(msgToSign_2)

      // create command arguments
      val removeCmdInput_2 = RemoveStakeCmdInput(expectedStake2.stakeId, msgSignature_2)

      val msg6 = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput_2.encode(), nonce4)
      val txHash6 = Keccak256.hash("sixth tx")
      view.setupTxContext(txHash6, 10)

      val returnData6 = assertGas(40481, msg6, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData6)
      assertArrayEquals(expectedStake2.stakeId, returnData6)

      getAllStakesReturnData = assertGas(25200, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      listOfExpectedForgerStakes.add(expectedStake3)
      listOfExpectedForgerStakes.add(expectedStake4)
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)
      checkForgerStakeStorageV2Consistency(view, expectedStake3.stakeId)
      checkForgerStakeStorageV2Consistency(view, expectedStake4.stakeId)
      assertArrayEquals(NULL_HEX_STRING_32, ForgerStakeStorageV2.forgerStakeArray.getValue(view, 2))

      // remove stake id 4
      val nonce5 = randomNonce

      val msgToSign_3 = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(expectedStake4.stakeId, origin, nonce5.toByteArray)
      val msgSignature_3 = privateKey.sign(msgToSign_3)

      // create command arguments
      val removeCmdInput_3 = RemoveStakeCmdInput(expectedStake4.stakeId, msgSignature_3)

      val msg7 = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput_3.encode(), nonce5)
      val txHash7 = Keccak256.hash("seventh tx")
      view.setupTxContext(txHash7, 10)

      // try processing the removal of stake, should succeed
      val returnData7 = assertGas(26081, msg7, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData7)
      assertArrayEquals(expectedStake4.stakeId, returnData7)

      assertEquals(validWeiAmount, view.getBalance(contractAddress))
      assertEquals(expOriginBalance, view.getBalance(origin))
      assertEquals(BigInteger.ZERO, view.getBalance(ownerAddressProposition2.address()))
      assertEquals(validWeiAmount.multiply(BigInteger.valueOf(3)), view.getBalance(ownerAddressProposition.address()))

      getAllStakesReturnData = assertGas(14700, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)

      listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      listOfExpectedForgerStakes.add(expectedStake3)
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)
      checkForgerStakeStorageV2Consistency(view, expectedStake3.stakeId)
      assertArrayEquals(NULL_HEX_STRING_32, ForgerStakeStorageV2.forgerStakeArray.getValue(view, 1))

      // remove last stake id
      val nonce6 = randomNonce

      val msgToSign_4 = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(expectedStake3.stakeId, origin, nonce6.toByteArray)
      val msgSignature_4 = privateKey2.sign(msgToSign_4)

      // create command arguments
      val removeCmdInput_4 = RemoveStakeCmdInput(expectedStake3.stakeId, msgSignature_4)

      val msg8 = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput_4.encode(), nonce6)
      val txHash8 = Keccak256.hash("eighth tx")
      view.setupTxContext(txHash8, 10)

      // try processing the removal of stake, should succeed
      val returnData8 = assertGas(26081, msg8, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData8)
      assertArrayEquals(expectedStake3.stakeId, returnData8)

      assertEquals(BigInteger.ZERO, view.getBalance(contractAddress))
      assertEquals(expOriginBalance, view.getBalance(origin))
      assertEquals(validWeiAmount, view.getBalance(ownerAddressProposition2.address()))
      assertEquals(validWeiAmount.multiply(BigInteger.valueOf(3)), view.getBalance(ownerAddressProposition.address()))

      getAllStakesReturnData = assertGas(4200, getAllStakesMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)

      listOfExpectedForgerStakes = new util.ArrayList[AccountForgingStakeInfo]
      checkListOfStakesV2(view, getAllStakesReturnData, listOfExpectedForgerStakes)

      // Check that the forgerStakeArray is really empty
      assertEquals(0,  ForgerStakeStorageV2.forgerStakeArray.getSize(view))
      assertArrayEquals(NULL_HEX_STRING_32, ForgerStakeStorageV2.forgerStakeArray.getValue(view, 0))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  private def checkListOfStakesV2(view: BaseAccountStateView, forgerStakesListRawData: Array[Byte], expectedStakeList: util.List[AccountForgingStakeInfo]): Unit= {
    assertNotNull(forgerStakesListRawData)
    assertArrayEquals(AccountForgingStakeInfoListEncoder.encode(expectedStakeList), forgerStakesListRawData)
    assertEquals(expectedStakeList.size(), ForgerStakeStorageV2.forgerStakeArray.getSize(view))

    for (i <- 0 until expectedStakeList.size()) {
      assertArrayEquals(expectedStakeList.get(i).stakeId, ForgerStakeStorageV2.forgerStakeArray.getValue(view, i))
    }

  }

  private def checkForgerStakeStorageV2Consistency(view: BaseAccountStateView, stakeId: Array[Byte]): Unit = {
    val stakeData = ForgerStakeStorageV2.findForgerStakeStorageElem(view, stakeId).get.asInstanceOf[ForgerStakeStorageElemV2]
    assertArrayEquals("Stake array is invalid", stakeId, ForgerStakeStorageV2.forgerStakeArray.getValue(view, stakeData.stakeListIndex))
    val ownerInfo = OwnerStakeInfo(stakeData.ownerPublicKey)
    assertArrayEquals("Owner stake array is invalid", stakeId, ownerInfo.getValue(view, stakeData.ownerListIndex))
  }


  @Test
  def testUpgrade(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))

      // Create some stakes with old storage model
      val privateKey1: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest1".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition1: AddressProposition = privateKey1.publicImage()
      val (listOfExpectedForgerStakes1, _) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition1, 40, blockContextForkV1_3)

      val privateKey2: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest2".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition2: AddressProposition = privateKey2.publicImage()
      val (listOfExpectedForgerStakes2, _) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition2, 50, blockContextForkV1_3)

      val privateKey3: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest3".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition3: AddressProposition = privateKey3.publicImage()
      val (listOfExpectedForgerStakes3, _) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition3, 100, blockContextForkV1_3)

      // I need to reverse the list because in the old model the iteration starts from the last stake and goes backward
      val expectedList = (listOfExpectedForgerStakes1 ++ listOfExpectedForgerStakes2 ++ listOfExpectedForgerStakes3).reverse.asJava

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      assertGas(0, upgradeMsg, view, forgerStakeMessageProcessor, blockContextForkV1_3)

      val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)
      val returnData = assertGas(1999200, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      checkListOfStakesV2(view,returnData, expectedList)

      // Checking log
      val listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedEvent = StakeUpgrade(ForgerStakeStorageVersion.VERSION_1.id, ForgerStakeStorageVersion.VERSION_2.id)
      checkUpgradeStakeEvent(expectedEvent, listOfLogs(0))

      view.commit(bytesToVersion(getVersion.data()))

    }
  }


  @Ignore
  def testGetListOfForgersLotOfStakes(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = ZenWeiConverter.MAX_MONEY_IN_WEI
      createSenderAccount(view, initialAmount)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))

      val (list_of_stakes, _) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition, 10000, blockContextForkV1_3)
      val msg4 = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)
      val returnData4 = assertGas(108801000, msg4, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData4)
      val expectedListData1 = AccountForgingStakeInfoListEncoder.encode(list_of_stakes.asJava)
      assertArrayEquals(expectedListData1, returnData4)
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testGetPagedForgersStakesOfUser(): Unit = {

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(ZenWeiConverter.MAX_MONEY_IN_WEI)
      createSenderAccount(view, initialAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      var nonce = 0
      var cmdInput = GetPagedForgersStakesOfUserCmdInput(
        ownerAddressProposition.address(),
        0,
        10
      )

      // Test with the correct signature before fork. It should fail.
      var msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(GetPagedForgersStakesOfUserCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      // should fail because, before Version 1.3 fork, GetPagedForgersStakesOfUserCmd is not a valid function signature
      val blockContextBeforeFork = new BlockContext(
        Address.ZERO,
        0,
        0,
        DefaultGasFeeFork.blockGasLimit,
        0,
        V1_3_MOCK_FORK_POINT - 1,
        0,
        1,
        MockedHistoryBlockHashProvider,
        Hash.ZERO
      )

      var exc = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextBeforeFork)
      }
      assertEquals(s"op code not supported: $GetPagedForgersStakesOfUserCmd", exc.getMessage)

      // Test after fork.

      // Test before calling "upgrade". It should fail

      exc = intercept[ExecutionRevertedException] {
        assertGas(GasUtil.ColdSloadCostEIP2929, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"Forger stake storage not upgraded yet", exc.getMessage)

      // Calling upgrade
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))

      // Test without any stake
      var returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      val expectedListOfStakes = Seq.empty[AccountForgingStakeInfo]
      var res = PagedListOfStakesOutputDecoder.decode(returnData)
      assertEquals(-1, res.nextStartPos)
      assertEquals(expectedListOfStakes, res.listOfStakes)

      cmdInput = GetPagedForgersStakesOfUserCmdInput(
        ownerAddressProposition.address(),
        1,
        10
      )
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(GetPagedForgersStakesOfUserCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      var excIllegalArgumentException = intercept[IllegalArgumentException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      }
      assertTrue(excIllegalArgumentException.getMessage.startsWith("Invalid position where to start reading forger stakes"))

      // Check that it is not payable
      val value = validWeiAmount
      msg = getMessage(
        contractAddress, value, BytesUtils.fromHexString(GetPagedForgersStakesOfUserCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      val excPayable = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"Call value must be zero", excPayable.getMessage)


      // Check for wrong input data
      val badData = Bytes.concat(cmdInput.encode(), new Array[Byte](1))
      msg = getMessage(contractAddress, BigInteger.ZERO, BytesUtils.fromHexString(GetPagedForgersStakesOfUserCmd) ++ badData, nonce, ownerAddressProposition.address())
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // Creates some stakes for different owners
      val expectedBlockSignerProposition = "aa22334455667788112233445586778811223344556677881122334455667788" // 32 bytes
      val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
      val expectedVrfKey = "aabbccddeeff0099aabb87ddeeff0099aabbccddeeff0099aabbccd2aeff001234"
      val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition, vrfPublicKey)
      ))

      val privateKey1: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest1".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition1: AddressProposition = privateKey1.publicImage()
      val (listOfExpectedForgerStakes1, _) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition1, 14, blockContextForkV1_3)

      val privateKey2: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest2".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition2: AddressProposition = privateKey2.publicImage()
      val (listOfExpectedForgerStakes2, _) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition2, 5, blockContextForkV1_3)

      val privateKey3: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest3".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition3: AddressProposition = privateKey3.publicImage()
      val (listOfExpectedForgerStakes3, _) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition3, 1, blockContextForkV1_3)

      // get stakes for owner 1

      // Check out of bound startPos
      cmdInput = GetPagedForgersStakesOfUserCmdInput(
        ownerAddressProposition1.address(),
        listOfExpectedForgerStakes1.size,
        5
      )
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(GetPagedForgersStakesOfUserCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      excIllegalArgumentException = intercept[IllegalArgumentException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      }
      assertTrue(excIllegalArgumentException.getMessage.startsWith("Invalid position where to start reading forger stakes"))


      def checkPagedResult(listOfExpectedForgerStakes: Seq[AccountForgingStakeInfo], owner: AddressProposition, startPos: Int, pageSize: Int): Unit = {
        require(pageSize > 0)
        val (page, remaining) = listOfExpectedForgerStakes.splitAt(pageSize)
        val cmdInput = GetPagedForgersStakesOfUserCmdInput(
          owner.address(),
          startPos,
          pageSize
        )
        val msg = getMessage(
          contractAddress, 0, BytesUtils.fromHexString(GetPagedForgersStakesOfUserCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())
        returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
        //Check getListOfForgers
        val res = PagedListOfStakesOutputDecoder.decode(returnData)
        assertEquals(page, res.listOfStakes)
        if (remaining.isEmpty)
          assertEquals(-1, res.nextStartPos)
        else {
          checkPagedResult(remaining, owner, res.nextStartPos, pageSize)
        }

      }

      var startPos = 0
      checkPagedResult(listOfExpectedForgerStakes1, ownerAddressProposition1, startPos, 5)
      checkPagedResult(listOfExpectedForgerStakes1, ownerAddressProposition1, startPos, 100)

      startPos = 3
      checkPagedResult(listOfExpectedForgerStakes1.drop(startPos), ownerAddressProposition1, startPos, 5)

      checkPagedResult(listOfExpectedForgerStakes2.drop(startPos), ownerAddressProposition2, startPos, 1)

      startPos = listOfExpectedForgerStakes3.size - 1
      checkPagedResult(listOfExpectedForgerStakes3.drop(startPos), ownerAddressProposition3, startPos, 1)


      // Try after removing one stake
      val nonce2 = randomNonce
      val stakeIdToRemove = listOfExpectedForgerStakes2(2).stakeId
      val msgToSign = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(stakeIdToRemove, origin, nonce2.toByteArray)
      val msgSignature = privateKey2.sign(msgToSign)

      // create command arguments
      val removeCmdInput = RemoveStakeCmdInput(stakeIdToRemove, msgSignature)

      val msgRemove = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput.encode(), nonce2)

      // try processing the removal of stake, should succeed
      returnData = assertGas(48481, msgRemove, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      assertNotNull(returnData)
      assertArrayEquals(stakeIdToRemove, returnData)


      //Check getListOfForgerStakes
      val newExpectedListOfForgerStakes2 = listOfExpectedForgerStakes2.splitAt(2)._1 ++ Seq(listOfExpectedForgerStakes2.last, listOfExpectedForgerStakes2(3))
      checkPagedResult(newExpectedListOfForgerStakes2, ownerAddressProposition2, 0, 3)

      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testStakeOf(): Unit = {

    def decodeStakeOfResult(returnData: Array[Byte]): BigInteger = {
      val inputParamsString = org.web3j.utils.Numeric.toHexString(returnData)
      val decoder = new DefaultFunctionReturnDecoder
      val listOfParamTypes = org.web3j.abi.Utils.convert(util.Arrays.asList(
        new TypeReference[Uint256]() {}
      ))

      val listOfParams = decoder.decodeFunctionResult(inputParamsString, listOfParamTypes)
      listOfParams.get(0).asInstanceOf[Uint256].getValue
    }

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(ZenWeiConverter.MAX_MONEY_IN_WEI)
      createSenderAccount(view, initialAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      var nonce = 0
      var cmdInput = StakeOfCmdInput(
        ownerAddressProposition.address()
      )

      // Test with the correct signature before fork. It should fail.
      var msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(StakeOfCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      // should fail because, before Version 1.3 fork, StakeOfCmd is not a valid function signature
      val blockContextBeforeFork = new BlockContext(
        Address.ZERO,
        0,
        0,
        DefaultGasFeeFork.blockGasLimit,
        0,
        V1_3_MOCK_FORK_POINT - 1,
        0,
        1,
        MockedHistoryBlockHashProvider,
        Hash.ZERO
      )

      var exc = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextBeforeFork)
      }
      assertEquals(s"op code not supported: $StakeOfCmd", exc.getMessage)

      // Test after fork.

      // Test before calling "upgrade". It should fail

      exc = intercept[ExecutionRevertedException] {
        assertGas(GasUtil.ColdSloadCostEIP2929, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"Forger stake storage not upgraded yet", exc.getMessage)

      // Calling upgrade
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))


      // Test without any stake
      var returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      assertEquals(BigInteger.ZERO, decodeStakeOfResult(returnData))

      // Check that it is not payable
      val value = validWeiAmount
      msg = getMessage(
        contractAddress, value, BytesUtils.fromHexString(StakeOfCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())

      val excPayable = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"Call value must be zero", excPayable.getMessage)

      // Check for wrong input data
      val badData = Bytes.concat(cmdInput.encode(), new Array[Byte](1))
      msg = getMessage(contractAddress, BigInteger.ZERO, BytesUtils.fromHexString(StakeOfCmd) ++ badData, nonce, ownerAddressProposition.address())
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // Creates some stakes for different owners
      val expectedBlockSignerProposition = "aa22334455667788112233445586778811223344556677881122334455667788" // 32 bytes
      val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(expectedBlockSignerProposition)) // 32 bytes
      val expectedVrfKey = "aabbccddeeff0099aabb87ddeeff0099aabbccddeeff0099aabbccd2aeff001234"
      val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(expectedVrfKey)) // 33 bytes

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq(
        (blockSignerProposition, vrfPublicKey)
      ))

      val privateKey1: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest1".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition1: AddressProposition = privateKey1.publicImage()
      val (_, expectedAmount1) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition1, 3, blockContextForkV1_3)

      val privateKey2: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest2".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition2: AddressProposition = privateKey2.publicImage()
      val (_, expectedAmount2) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition2, 1, blockContextForkV1_3)

      val privateKey3: PrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("nativemsgprocessortest3".getBytes(StandardCharsets.UTF_8))
      val ownerAddressProposition3: AddressProposition = privateKey3.publicImage()
      val (listOfExpectedStakes3, expectedAmount3) = addStakes(view, blockSignerProposition, vrfPublicKey, ownerAddressProposition3, 5, blockContextForkV1_3)

      cmdInput = StakeOfCmdInput(
        ownerAddressProposition1.address()
      )
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(StakeOfCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())
      val returnData1 = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      assertEquals(expectedAmount1, decodeStakeOfResult(returnData1))


      cmdInput = StakeOfCmdInput(
        ownerAddressProposition2.address()
      )
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(StakeOfCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())
      val returnData2 = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      assertEquals(expectedAmount2, decodeStakeOfResult(returnData2))

      cmdInput = StakeOfCmdInput(
        ownerAddressProposition3.address()
      )
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(StakeOfCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())
      val returnData3 = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      assertEquals(expectedAmount3, decodeStakeOfResult(returnData3))


      // Try after removing one stake
      val nonce2 = randomNonce
      val stakeIdToRemove = listOfExpectedStakes3(3).stakeId
      val msgToSign = ForgerStakeMsgProcessor.getRemoveStakeCmdMessageToSign(stakeIdToRemove, origin, nonce2.toByteArray)
      val msgSignature = privateKey3.sign(msgToSign)

      // create command arguments
      val removeCmdInput = RemoveStakeCmdInput(stakeIdToRemove, msgSignature)

      val msgRemove = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd) ++ removeCmdInput.encode(), nonce2)

      returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msgRemove, view, blockContextForkV1_3, _))
      assertNotNull(returnData)


      cmdInput = StakeOfCmdInput(
        ownerAddressProposition3.address()
      )
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(StakeOfCmd) ++ cmdInput.encode(), nonce, ownerAddressProposition.address())
      returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))

      assertEquals(expectedAmount3.subtract(listOfExpectedStakes3(3).forgerStakeData.stakedAmount), decodeStakeOfResult(returnData))
      view.commit(bytesToVersion(getVersion.data()))

    }
  }

  @Test
  def testInvalidRemoveStakeCmdV2(): Unit = {
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
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))


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
        TestContext.process(forgerStakeMessageProcessor, addNewStakeMsg, view, blockContextForkV1_3, _)
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
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      }

      // should fail because value in msg should be 0 (value=-1)
      msg = getMessage(contractAddress, BigInteger.valueOf(-1), BytesUtils.fromHexString(RemoveStakeCmd) ++ data, nonce)
      assertThrows[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      }

      // should fail because input data has a trailing byte
      val badData = Bytes.concat(data, new Array[Byte](1))
      msg = getMessage(contractAddress, BigInteger.ZERO, BytesUtils.fromHexString(RemoveStakeCmd) ++ badData, nonce)
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
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
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, _))
      }
      assertTrue(ex2.getMessage.contains("ill-formed signature"))

      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testRejectSendingInvalidValueToGetAllForgerStakesV2(): Unit = {
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
      val upgradeMsg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), randomNonce, ownerAddressProposition.address())
      withGas(TestContext.process(forgerStakeMessageProcessor, upgradeMsg, view, blockContextForkV1_3, _))

      withGas { gas =>
        var msg = getMessage(contractAddress, BigInteger.ONE, BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)

        assertThrows[ExecutionRevertedException] {
          TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, gas)
        }

        msg = getMessage(contractAddress, BigInteger.valueOf(-1), BytesUtils.fromHexString(GetListOfForgersCmd), randomNonce)

        assertThrows[ExecutionRevertedException] {
          TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_3, gas)
        }
      }
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  //This test is for chains that use the V2 storage model from the beginning, activating Fork 1.3
  @Test
  def testInitV2(): Unit = {

    val forkConfigurator = new SimpleForkConfigurator() {
      override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] = List(

        new Pair[SidechainForkConsensusEpoch, OptionalSidechainFork](
          SidechainForkConsensusEpoch(0, 0, 0),
          new Version1_3_0Fork(true)
        )
      ).asJava
    }
    ForkManagerUtil.initializeForkManager(forkConfigurator, "regtest")

    usingView(forgerStakeMessageProcessor) { view =>
      // we have to call init beforehand
      assertFalse(view.accountExists(contractAddress))
      assertEquals(ForgerStakeStorageVersion.VERSION_1, getStorageVersionFromDb(view))

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      assertTrue(view.accountExists(contractAddress))
      assertTrue(view.isSmartContractAccount(contractAddress))

      assertEquals(ForgerStakeStorageVersion.VERSION_2, getStorageVersionFromDb(view))

      // Check that "upgrade" cannot be called twice
      val nonce = 0
      // Test upgrade before reaching the fork point. It should fail.
      val msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), nonce, ownerAddressProposition.address())
      val exc = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"Forger stake storage already upgraded", exc.getMessage)
      view.commit(bytesToVersion(getVersion.data()))
    }
  }

  @Test
  def testDisable(): Unit = {

    usingView(forgerStakeMessageProcessor) { view =>

      forgerStakeMessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(10).multiply(ZenWeiConverter.MAX_MONEY_IN_WEI)
      createSenderAccount(view, initialAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      var nonce = 0

      // Test with the correct signature before fork. It should fail.
      var msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(DisableCmd), nonce, ownerAddressProposition.address())

      // should fail because, before Version 1.4 fork, DisableCmd is not a valid function signature
      var exc = intercept[ExecutionRevertedException] {
        assertGas(0, msg, view, forgerStakeMessageProcessor, blockContextForkV1_3)
      }
      assertEquals(s"op code not supported: $DisableCmd", exc.getMessage)

      // Test after fork.
      // Check that it is not payable
      val value = validWeiAmount
      msg = getMessage(
        contractAddress, value, BytesUtils.fromHexString(DisableCmd), nonce, ownerAddressProposition.address())

      val excPayable = intercept[ExecutionRevertedException] {
        assertGas(2100, msg, view, forgerStakeMessageProcessor, blockContextForkV1_4)
      }
      assertEquals(s"Call value must be zero", excPayable.getMessage)

      // Check for wrong input data
      val badData = new Array[Byte](1)
      msg = getMessage(contractAddress, BigInteger.ZERO, BytesUtils.fromHexString(DisableCmd) ++ badData, nonce, ownerAddressProposition.address())
      exc = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(exc.getMessage.contains("invalid msg data length"))


      // Test that disable can be called only by Forger stake contract V2
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(DisableCmd), nonce, ownerAddressProposition.address())
      exc = intercept[ExecutionRevertedException] {
        assertGas(2100, msg, view, forgerStakeMessageProcessor, blockContextForkV1_4)
      }
      assertEquals(s"Authorization failed", exc.getMessage)

      // Test that disable can be called not only by Forger stake contract V2 address but it needs also the hashcode (i.e.
      // that it is initialized)
      msg = getMessage(
        contractAddress, 0, BytesUtils.fromHexString(DisableCmd), nonce, WellKnownAddresses.FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)

      exc = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertEquals(s"Authorization failed", exc.getMessage)

      ForgerStakeV2MsgProcessor.init(view, blockContextForkV1_4.consensusEpochNumber)

      assertGas(22950, msg, view, forgerStakeMessageProcessor, blockContextForkV1_4)

      val listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      assertEquals("Wrong address", contractAddress, listOfLogs.head.address)
      assertEquals("Wrong number of topics", 1, listOfLogs.head.topics.length) //The first topic is the hash of the signature of the event
      assertArrayEquals("Wrong event signature", DisableEventSig, listOfLogs.head.topics(0).toBytes)


      // Check that the old stakes methods cannot be called anymore
      // Don't care about actual data, because it should be stopped before unmarshaling
      exc = intercept[ExecutionRevertedException] {
        msg = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(AddNewStakeCmd), randomNonce)
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

     exc = intercept[ExecutionRevertedException] {
       val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(RemoveStakeCmd), nonce)
       withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

      exc = intercept[ExecutionRevertedException] {
        val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetPagedListOfForgersCmd), nonce)
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

      exc = intercept[ExecutionRevertedException] {
        val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfForgersCmd), nonce)
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

      exc = intercept[ExecutionRevertedException] {
        val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(UpgradeCmd), nonce)
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

      exc = intercept[ExecutionRevertedException] {
        val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(StakeOfCmd), nonce)
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

      exc = intercept[ExecutionRevertedException] {
        val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetPagedForgersStakesOfUserCmd), nonce)
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

      exc = intercept[ExecutionRevertedException] {
        val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(DisableCmd), nonce)
        withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContextForkV1_4, _))
      }
      assertTrue(s"Wrong error message ${exc.getMessage}", exc.getMessage.contains("disabled"))

    }
  }



  private def addStakes(view: AccountStateView,
                        blockSignerProposition: PublicKey25519Proposition,
                        vrfPublicKey: VrfPublicKey,
                        ownerAddressProposition1: AddressProposition,
                        numOfStakes: Int,
                        blockContext: BlockContext): (Seq[AccountForgingStakeInfo], BigInteger) = {
    val cmdInput1 = AddNewStakeCmdInput(
      ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
      ownerAddressProposition1.address()
    )
    val data: Array[Byte] = cmdInput1.encode()

    var listOfForgerStakes = Seq[AccountForgingStakeInfo]()

    var totalAmount = BigInteger.ZERO
    for (i <- 1 to numOfStakes) {
      val stakeAmount = validWeiAmount.multiply(BigInteger.valueOf(i))
      val nonce = randomNonce
      val msg = getMessage(contractAddress, stakeAmount,
        BytesUtils.fromHexString(AddNewStakeCmd) ++ data, nonce)
      val expStakeId = forgerStakeMessageProcessor.getStakeId(msg)
      listOfForgerStakes = listOfForgerStakes :+ AccountForgingStakeInfo(expStakeId,
        ForgerStakeData(ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
          ownerAddressProposition1, stakeAmount))
      val returnData = withGas(TestContext.process(forgerStakeMessageProcessor, msg, view, blockContext, _))
      assertNotNull(returnData)
      totalAmount = totalAmount.add(stakeAmount)
    }
    (listOfForgerStakes, totalAmount)
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

  def checkUpgradeStakeEvent(expectedEvent: StakeUpgrade, actualEvent: EthereumConsensusDataLog): Unit = {
    assertEquals("Wrong address", contractAddress, actualEvent.address)
    assertEquals("Wrong number of topics", 1, actualEvent.topics.length) //The first topic is the hash of the signature of the event
    assertArrayEquals("Wrong event signature", StakeUpgradeEventSig, actualEvent.topics(0).toBytes)

    val listOfRefs = util.Arrays
      .asList(
        TypeReference.makeTypeReference(expectedEvent.oldVersion.getTypeAsString),
        TypeReference.makeTypeReference(expectedEvent.newVersion.getTypeAsString)
      )
      .asInstanceOf[util.List[TypeReference[Type[_]]]]

    val listOfDecodedData = FunctionReturnDecoder.decode(BytesUtils.toHexString(actualEvent.data), listOfRefs)
    assertEquals("Wrong oldVersion in data", expectedEvent.oldVersion, listOfDecodedData.get(0))
    assertEquals("Wrong newVersion in data", expectedEvent.newVersion, listOfDecodedData.get(1))
  }

}

object PagedListOfStakesOutputDecoder
  extends ABIDecoder[PagedListOfStakesOutput]
    with MsgProcessorInputDecoder[PagedListOfStakesOutput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] = {
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Int32]() {},
      new TypeReference[DynamicArray[AccountForgingStakeInfoABI]]() {}
    ))
  }

  override def createType(listOfParams: util.List[Type[_]]): PagedListOfStakesOutput = {
    val startPos = listOfParams.get(0).asInstanceOf[Int32].getValue.intValueExact()
    val listOfStaticStruct = listOfParams.get(1).asInstanceOf[DynamicArray[AccountForgingStakeInfoABI]].getValue.asScala
    val list = listOfStaticStruct.map(x => AccountForgingStakeInfo(x.stakeId,
      ForgerStakeData(
        ForgerPublicKeys(new PublicKey25519Proposition(x.pubKey),
          new VrfPublicKey(x.vrf1 ++ x.vrf2)),
        new AddressProposition(new Address(x.owner)),
        x.amount)))
    PagedListOfStakesOutput(startPos, list.toSeq)
  }
}
