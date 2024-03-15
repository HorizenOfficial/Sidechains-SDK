package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.{ABIDecoder, MsgProcessorInputDecoder}
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.fork.{Version1_2_0Fork, Version1_3_0Fork, Version1_4_0Fork}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.secret.{PrivateKeySecp256k1, PrivateKeySecp256k1Creator}
import ForgerStakeV2MsgProcessor._
import io.horizen.account.state.ForgerStakeStorage.getStorageVersionFromDb
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state.events.{DelegateForgerStake, OpenForgerList, StakeUpgrade, WithdrawForgerStake}
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.utils.{EthereumTransactionDecoder, ZenWeiConverter}
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
import io.horizen.account.state.nativescdata.forgerstakev2.{DelegateCmdInput, PagedForgersStakesByDelegatorCmdInput, PagedForgersStakesByForgerCmdInput, StakeTotalCmdInput, WithdrawCmdInput}
import io.horizen.fork.{ForkConfigurator, ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch}
import io.horizen.consensus.intToConsensusEpochNumber

class ForgerStakeV2MsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with MessageProcessorFixture
    with StoreFixture {

  val dummyBigInteger: BigInteger = BigInteger.ONE
  val negativeAmount: BigInteger = BigInteger.valueOf(-1)

  val invalidWeiAmount: BigInteger = new BigInteger("10000000001")
  val validWeiAmount: BigInteger = new BigInteger("10000000000")

  val mockNetworkParams: NetworkParams = mock[NetworkParams]
  val forgerStakeV2MessageProcessor: ForgerStakeV2MsgProcessor = ForgerStakeV2MsgProcessor(mockNetworkParams)

  /** short hand: forger state native contract address */
  val contractAddress: Address = forgerStakeV2MessageProcessor.contractAddress

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

  val scAddrStr1: String = "00C8F107a09cd4f463AFc2f1E6E5bF6022Ad4600"
  val scAddressObj1 = new Address("0x" + scAddrStr1)

  val V1_4_MOCK_FORK_POINT: Int = 300

  val blockContextForkV1_4 =  new BlockContext(
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



  class TestOptionalForkConfigurator extends ForkConfigurator {
    override val fork1activation: SidechainForkConsensusEpoch = SidechainForkConsensusEpoch(0, 0, 0)
    override def getOptionalSidechainForks: util.List[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]] =
      Seq[Pair[SidechainForkConsensusEpoch, OptionalSidechainFork]](
        new Pair(SidechainForkConsensusEpoch(V1_4_MOCK_FORK_POINT, V1_4_MOCK_FORK_POINT, V1_4_MOCK_FORK_POINT), Version1_4_0Fork(true)),
      ).asJava
  }


  @Before
  def init(): Unit = {
    ForkManagerUtil.initializeForkManager(new TestOptionalForkConfigurator, "regtest")
    // by default start with fork active
    Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(Option(intToConsensusEpochNumber(V1_4_MOCK_FORK_POINT)))
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


  @Test
  def testInit(): Unit = {
    usingView(forgerStakeV2MessageProcessor) { view =>
      // we have to call init beforehand
      assertTrue(forgerStakeV2MessageProcessor.isForkActive(view.getConsensusEpochNumberAsInt))
      assertFalse(view.accountExists(contractAddress))
      forgerStakeV2MessageProcessor.init(view, view.getConsensusEpochNumberAsInt)
      assertTrue(view.accountExists(contractAddress))
      assertTrue(view.isSmartContractAccount(contractAddress))
      view.commit(bytesToVersion(getVersion.data()))
    }
  }


  @Test
  def testAddAndRemoveStake(): Unit = {

    val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
    val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

    usingView(forgerStakeV2MessageProcessor) { view =>

      forgerStakeV2MessageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)
      createSenderAccount(view, initialAmount)

      Mockito.when(mockNetworkParams.restrictForgers).thenReturn(true)
      Mockito.when(mockNetworkParams.allowedForgersList).thenReturn(Seq((blockSignerProposition, vrfPublicKey)))

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      val delegateCmdInput = DelegateCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey)
      )

      val data: Array[Byte] = delegateCmdInput.encode()
      val msg = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(DelegateCmd) ++ data, randomNonce)

      // positive case, verify we can add the stake to view
      val returnData = assertGas(0, msg, view, forgerStakeV2MessageProcessor, blockContextForkV1_4)
      assertNotNull(returnData)
      println("This is the returned value: " + BytesUtils.toHexString(returnData))

      //TODO: add checks...

      //withdrwaw
      val withdrawInput = WithdrawCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey), 10
      )

      val data2: Array[Byte] = withdrawInput.encode()
      val msg2 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(WithdrawCmd) ++ data2, randomNonce)

      val returnData2 = assertGas(0, msg2, view, forgerStakeV2MessageProcessor, blockContextForkV1_4)
      assertNotNull(returnData2)
      println("This is the returned value: " + BytesUtils.toHexString(returnData2))

      //TODO: add checks...

      //stake total

      val stakeTotalInput = StakeTotalCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        scAddressObj1,
        5,
        5
      )

      val data3: Array[Byte] = stakeTotalInput.encode()
      val msg3 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(StakeTotalCmd) ++ data3, randomNonce)
      val returnData3= assertGas(0, msg2, view, forgerStakeV2MessageProcessor, blockContextForkV1_4)
      assertNotNull(returnData3)
      println("This is the returned value: " + BytesUtils.toHexString(returnData2))

      //TODO: add checks...

      //GetPagedForgersStakesByForger

      val pagedForgersStakesByForgerCmd = PagedForgersStakesByForgerCmdInput(
        ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
        5,
        5
      )

      val data4: Array[Byte] = pagedForgersStakesByForgerCmd.encode()
      val msg4 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(GetPagedForgersStakesByForgerCmd) ++ data3, randomNonce)
      val returnData4 = assertGas(0, msg2, view, forgerStakeV2MessageProcessor, blockContextForkV1_4)
      assertNotNull(returnData4)
      println("This is the returned value: " + BytesUtils.toHexString(returnData2))

      //TODO: add checks...

      //GetPagedForgersStakesByDelegator

      val pagedForgersStakesByDelegatorCmd = PagedForgersStakesByDelegatorCmdInput(
        scAddressObj1,
        5,
        5
      )

      val data5: Array[Byte] = pagedForgersStakesByDelegatorCmd.encode()
      val msg5 = getMessage(contractAddress, validWeiAmount, BytesUtils.fromHexString(GetPagedForgersStakesByDelegatorCmd) ++ data5, randomNonce)
      val returnData5 = assertGas(0, msg5, view, forgerStakeV2MessageProcessor, blockContextForkV1_4)
      assertNotNull(returnData5)
      println("This is the returned value: " + BytesUtils.toHexString(returnData2))

      //TODO: add checks...
    }
  }

}


