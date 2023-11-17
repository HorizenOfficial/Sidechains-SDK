package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.fork.ZenDAOFork
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.state.McAddrOwnershipMsgProcessor._
import io.horizen.account.state.events.{AddMcAddrOwnership, RemoveMcAddrOwnership}
import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.utils.BigIntegerUInt256.getUnsignedByteArray
import io.horizen.account.utils.Secp256k1.{PUBLIC_KEY_SIZE, SIGNATURE_RS_SIZE}
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.consensus.intToConsensusEpochNumber
import io.horizen.evm.Address
import io.horizen.fixtures.StoreFixture
import io.horizen.fork.{ForkConfigurator, ForkManagerUtil, OptionalSidechainFork, SidechainForkConsensusEpoch}
import io.horizen.params.NetworkParams
import io.horizen.utils.BytesUtils.{padWithZeroBytes, toHorizenPublicKeyAddress}
import io.horizen.utils.Utils.Ripemd160Sha256Hash
import io.horizen.utils.{BytesUtils, Pair}
import org.junit.Assert._
import org.junit._
import org.mockito.Mockito
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{FunctionReturnDecoder, TypeReference}
import org.web3j.crypto.{Keys, Sign}
import org.web3j.utils.Numeric
import sparkz.core.bytesToVersion
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import java.util.Optional
import scala.jdk.CollectionConverters.seqAsJavaListConverter

class McAddrOwnershipMsgProcessorMultisigTest
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
  val scAddressObj1 = new Address("0x" + scAddrStr1)

  //-------------------------------------------------------------------------------------------------------------------------------
  val mcMultiSigAddr1: String = "zr5VjXGbJDnWW2K6JM88uuG8X83T6Cgq1iT"
  val redeemScript1: String = "522102b1fb3c3b174b6a878e07f50c8057afe027d382c55b6ba9d91fa72e064479b6d32103db06cb236d4be6968deb8d829ca879ce254805f8dc9ea79586de428b7484b4cf21036509e8a548e4680d7e01fd32de53281054ee37022c622863d0a23c7258d4618b53ae"

  val mcAddrStr_11: String = "ztZxhE8FLc5biXSffbNtvTUUw7sZ9dcp2Du"
  val mcAddrStr_12: String = "ztbPhVg29w5Bjx3t4MjUmfeijUejUs5VEWR"
  val mcAddrStr_13: String = "ztrPYtTwCrVcbHiW8NZmdZDhvEWremL7nbU"

  val mcSignatureStr_11: String = "H19keJ3XHiMKgMBs1YPlB23a74fp82hsP/BgVbWuTnnofkNS6xuzDn/VtFpI0Tg1jZXDnTrCVUgMOuDUiTJUVkE="
  val mcSignatureStr_12: String = "H8sSwd4E9/j0s9sOA/udo8v+IlmwBuhvJR5QEN5FjeyoKnodzbnMDR8yKIa7sFOZlltBmvqazc13Y4JZWSWM1TY="
  val mcSignatureStr_13: String = "ILWR5DWahVgYcLV8bQuBeUsC6k5DVlrBLEpiVGmoY6LXUblD5EejUzT6paK3tpXdsBcfAWv/tP97xQAnZCwJDeg="

  var listOfMcMultisigAddrSign_1: Seq[String] = Seq[String](mcSignatureStr_11, mcSignatureStr_12, mcSignatureStr_13)
  //-------------------------------------------------------------------------------------------------------------------------------
  val mcMultiSigAddr2: String = "zrDVBZrgyp7r4ripx7gAaf9AoFVZ8dktgSX"
  val redeemScript2: String = "5241049bff9c9b5d0976aed138669e050b6b202aba89d16b5d3dfe4952a87d1888418fcae1a517d1dca6c61a7326dcb2f76bd5ff713b2c30f11a68a818bb074a2d40232103b881310ef3be9b8f42ad1705180d8eeed727fe47673a5119b6b1fe37309be9512102828b14acc47d4b7ba3f721b5eede0b85e4849cadb4bc59fc9aaaba75f579daea53ae"

  val mcAddrStr_21: String = "ztTw2K532ewo9gynBJv7FFUgbD19Wpifv8G"
  val mcAddrStr_22: String = "zthyXpQ2LMYrHLxwVC1R3yfpR6epqNrRDMz"
  val mcAddrStr_23: String = "ztp4GJMoLSQhuunZPC7x67wZW1YeKkWqb6Q"

  val mcSignatureStr_21: String = "H2ay43UtkU6vC+fA9mH41Y3aFnTaIeYYe/2jXycV5AkXeNTMOgs9PZtyAAP+1k7ln5v2PthgOzSV15P85WZVhlA="
  val mcSignatureStr_22: String = "IAsfMcq4+8nCxGxYUVXCiMBEMxWhAGPR7+4e/zwKnanxF++H0OlbF/LxUH+NWKVdtP0z3OquOK4UEtCmN5cfV8k="
  val mcSignatureStr_23: String = "H+Fn/va1Ct/BcyjokrrcSfzsvnk/19KKwNbWKrKWUPhvaGDy+uBsGPuQF39wIrds2mt9O62ZbOhh/T3jCSrd0a4="

  var listOfMcMultisigAddrSign_2: Seq[String] = Seq[String](mcSignatureStr_21, mcSignatureStr_22, mcSignatureStr_23)
  //-------------------------------------------------------------------------------------------------------------------------------
  // A multisig address made of 3 identical pub keys.
  val mcMultiSigAddrRep: String = "zr5Z3wKHPabDbr8vVXzW1JXdmZ55WRTcEN8"
  val redeemScriptRep: String = "522103dc080dc6ddc4dfeed866cf09e2d883babc6cc32ff6796f6ceed9d5f714a9cf452103dc080dc6ddc4dfeed866cf09e2d883babc6cc32ff6796f6ceed9d5f714a9cf452103dc080dc6ddc4dfeed866cf09e2d883babc6cc32ff6796f6ceed9d5f714a9cf4553ae"
  val mcSignatureStrRep: String = "Hw1T7OCaewGVgqjq+4FX+QczfYSwQJWzmb6D91qfnpmBGjO+WuuxYzcycspRaMChUzDJVEE7yLbd1SxeBxFP5Lk="
  var listOfMcMultisigAddrSign_rep: Seq[String] = Seq[String](mcSignatureStrRep, mcSignatureStrRep)
  //-------------------------------------------------------------------------------------------------------------------------------
  val mcMultiSigAddrBig: String = "zrSNRgWtnXoeDKsgWBPPBs2rFJEDq1hGpU6"
  val redeemScriptBig: String = "5f210240eb71c1faa869ef6fa575038e38185264874b2ab63bb5f8fb1b6bcc94d239a02102ede12c5f9c7990f40a2fd384223b970b7e210bfb97d57fc1824533100b89811a2102cb9986b088f164b739a1b007f7006ba627c539b597fc896355b75521df3cf7a021039056f14b5d7ec72686a8b3f0b9e43627f5b62056c79cde9fa361230c138bdc2721030cad99ba7cc045b9362dcff9f61b82edcc3bf45c5f376dd747881a8837495c432103baf9fdd1172c9049c612d2eece4b6fcd190cfd6f0c0c2073b9bf2090e83509e221034a2bad171d349cb10383ed0c3d39ce41add9715b670df80b9ee321bf88f4f01b2102f7cd52264a45ae5c84f47db6288b5ba0e5035abe529758550e56b5c4326351dd21027bdfe42fbb7abc80700892d095913509ede81dbb576959f10ce21a349332d2502102fc3841a7dd09470ed176e9bdbe0e778597888fed3b36514efdfa17bab359d89a2102d247302d65622b703203178fc57b5350ff24a05a523ecf9fdafe2eb6a2598d9821033ac3af609ba0a8522ed329d9dcb6250b7abacdac1c163aef232ab5a2bf4a00cf2102c4a59bde4fd83aad73fb3a423d24214facdc03dd93fc32e7ce664d6325c2de1b2102795a710a3a2bcc1be23578a84d127e801a458f831f504dccf256987ac17ce7562103d5d8ea992fbf8d8a66c376a18e824f880b5e809437c25bd7b9efe4c04724d9775fae"
  var listOfMcMultisigAddrSign_big: Seq[String] = Seq[String](
    "HwomseQg9FktQ3RIb+txbWpNpOFD2pTPwK5hhgt/CzW8Zq6na1bXFC18qpaOu2rdy/kDnLyQoZxL5aBF7XG0Exs=",
    "H6sz+uZZR+O4kP4aOwTfhrERG9bpixwk/p2/LA0KsPlAGK5GWWgrOPRQO+x6C2EVi+UpEE0fIsilY919W1JufGA=",
    "H5SG8jAxfWHELvoYM0NF5ROQYLjJGxdgJuXPsz5xtdfWQFj+U6mOV0sEk1SsJpDHNoHlpNCzPPGZi/PqgDOiNF4=",
    "HwvmoXsact7tammb92V2mDcfTGIUxj6bAg8IqSwKioBQIBpvWNe3FE7t/sDV1tBWq6AzJXKN42PPd1iCjBsZ+xk=",
    "IC+CPirr5/8Po5Ux/Zd2UdNEkVaXWIeqJVyK7FwZE4VHOHvNJuBAzb+96+2Z/HvX9sJ5hf0i4g35XbcUY/e4Aeo=",
    "IGLUfViNWcFsksy06yjEqWN8n+5kBOX1aAj7DhbEuy2TDAqfSL2T/LgRLaGxuaYPB1T4iTncFbkWj5NLlXsuYtA=",
    "HzVbp1yNFL+8giKiYNvZ/2g3DInWPg/p6WmBcu/c00ENAuYrH0vzg6HUG5P/m7OcMaaR1cww/y7DaEEDCsNPjmg=",
    "IMiOspm2c73TuEEuEkq5vml1MPyM5UYofzUxMVvnYpg+dfmVSaXDeMNzdK1aY5RVKByYMD+VgzUc2kEuw3yn6Yc=",
    "H2GSDj7UagXKxL5UCzRPDpEv79JoJGeUlJrjOacPXbaeVshEwZ6hOinElfBp8xsl4RUlgEuPYE1CuUknubboAHc=",
    "H01n7AjJXQc+WddsHAWeHwHddS5EH2ZZI8lUrVtjZFSRT8P+ute57bNhyNaluA4H21qce6mwo7gGILfYXhqV/7Q=",
    "IFgnnP7X7Xk7xzM+Y+OsVHe2t8JNMKHRVntWYulaltG6WRR1MfU6Ts0awPsnjfFNd7zEe+pRU8qJpJOdGPPoBmY=",
    "IEnF042X9IVfUzmXOCmKDKHiLGPpIABCdSJ6fLCDGUp5Im47RRwwSBZrBT7OOtMxT6ykcmUvBl/YDlxi0C8lKV8=",
    "IO8SecGtR/9CZVY8u2TjeXkNb+ZBNPNUhbXkLAsXIYuyC1hwfHW187SyyjxpYQIIrdv+p5+1MaBuodlaDeta2Zw=",
    "IONBPlExDo9a6H9Xv0JRwXchvqzuU5BIgG9LrjEgBkS0J8IxWccmC2ZjgTyw37lbBUupZf+rvJhJdTJA+iUlHbA=",
    "IJqxtg51fUNkpx/YtegEtv+BoGc6HvvHgMOqT+ZzhTJKBR4QoFAgR3adAbUN0yJLzaIBB/upCQwO9P2eu8vtxks=")




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
  val scAddressObj2 = new Address("0x" + scAddrStr2)
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
    assertEquals("Wrong MethodId for AddNewMultisigOwnershipCmd", "33c72ed1", McAddrOwnershipMsgProcessor.AddNewMultisigOwnershipCmd)
  }


  @Test
  def testAddAndRemoveMultisigOwnership(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      createSenderAccount(view, initialAmount, scAddressObj1)

      val cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr1, redeemScript1, listOfMcMultisigAddrSign_1)

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      val expectedOwnershipId = Keccak256.hash(mcMultiSigAddr1.getBytes(StandardCharsets.UTF_8))

      // positive case, verify we can add the data to view
      val returnData = assertGas(514937, msg, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData)

      assertArrayEquals(expectedOwnershipId, returnData)

      // Checking log
      var listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      var expectedAddEvt = AddMcAddrOwnership(scAddressObj1, mcMultiSigAddr1)
      checkAddNewOwnershipEvent(expectedAddEvt, listOfLogs(0))

      val txHash2 = Keccak256.hash("second tx")
      view.setupTxContext(txHash2, 10)
      // try processing a msg with the same data (same msg), should fail
      assertThrows[ExecutionRevertedException](withGas(TestContext.process(messageProcessor, msg, view, defaultBlockContext, _)))

      // Checking that log doesn't change
      listOfLogs = view.getLogs(txHash2)
      assertEquals("Wrong number of logs", 0, listOfLogs.length)

      // add a second association
      val cmdInput2 = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr2, redeemScript2, listOfMcMultisigAddrSign_2)

      val data2: Array[Byte] = cmdInput2.encode()
      val msg2 = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data2,
        randomNonce,
        scAddressObj1)

      val txHash3 = Keccak256.hash("third tx")
      view.setupTxContext(txHash3, 10)

      val returnData2 = assertGas(362037, msg2, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData2)

      // Checking log
      listOfLogs = view.getLogs(txHash3)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      expectedAddEvt = AddMcAddrOwnership(scAddressObj1, mcMultiSigAddr2)
      checkAddNewOwnershipEvent(expectedAddEvt, listOfLogs(0))

      // remove first ownership association
      val removeCmdInput1 = RemoveOwnershipCmdInput(Some(mcMultiSigAddr1))
      val msg3 = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput1.encode(),
        randomNonce, scAddressObj1
      )

      val txHash4 = Keccak256.hash("forth tx")
      view.setupTxContext(txHash4, 10)

      val returnData3 = assertGas(63437, msg3, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData3)

      // Checking log
      listOfLogs = view.getLogs(txHash4)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedRemoveEvt = RemoveMcAddrOwnership(scAddressObj1, mcMultiSigAddr1)
      checkRemoveNewOwnershipEvent(expectedRemoveEvt, listOfLogs(0))

      // get the list of ownerships via native smart contract interface, should return just the remaining one
      val expectedOwnershipData = McAddrOwnershipData(scAddrStr1, mcMultiSigAddr2)
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
  def testAddMultisigOwnershipRepetitionOfPubKeys(): Unit = {
    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      createSenderAccount(view, initialAmount, scAddressObj1)

      val cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddrRep, redeemScriptRep, listOfMcMultisigAddrSign_rep)

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      val expectedOwnershipId = Keccak256.hash(mcMultiSigAddrRep.getBytes(StandardCharsets.UTF_8))

      // positive case, verify we can add the data to view
      val returnData = assertGas(514937, msg, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData)

      assertArrayEquals(expectedOwnershipId, returnData)

      // Checking log
      val listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedAddEvt = AddMcAddrOwnership(scAddressObj1, mcMultiSigAddrRep)
      checkAddNewOwnershipEvent(expectedAddEvt, listOfLogs(0))

    }
  }

  @Test
  def testAddMultisigOwnershipLongShuffledSignatureList(): Unit = {

    // provide a large sequence of signatures, some of them are not verifying and the coorrect ones are shuffled so that the order of verification
    // is not the same of the public keys represented on the redeem script
    val shuffledSignatures = scala.util.Random.shuffle(listOfMcMultisigAddrSign_1++listOfMcMultisigAddrSign_2)

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      createSenderAccount(view, initialAmount, scAddressObj1)

      val cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr1, redeemScript1, shuffledSignatures)

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      val expectedOwnershipId = Keccak256.hash(mcMultiSigAddr1.getBytes(StandardCharsets.UTF_8))

      // positive case, verify we can add the data to view
      val returnData = assertGas(514937, msg, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData)

      assertArrayEquals(expectedOwnershipId, returnData)

      // Checking log
      val listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedAddEvt = AddMcAddrOwnership(scAddressObj1, mcMultiSigAddr1)
      checkAddNewOwnershipEvent(expectedAddEvt, listOfLogs(0))

    }
  }

  @Test
  def testAddMultisigOwnershipMaxNumOfPubKeys(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      createSenderAccount(view, initialAmount, scAddressObj1)

      val cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddrBig, redeemScriptBig, listOfMcMultisigAddrSign_big)

      val data: Array[Byte] = cmdInput.encode()
      val msg = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      val expectedOwnershipId = Keccak256.hash(mcMultiSigAddrBig.getBytes(StandardCharsets.UTF_8))

      // positive case, verify we can add the data to view
      val returnData = assertGas(514937, msg, view, messageProcessor, defaultBlockContext)
      assertNotNull(returnData)

      assertArrayEquals(expectedOwnershipId, returnData)

      // Checking log
      val listOfLogs = view.getLogs(txHash1)
      assertEquals("Wrong number of logs", 1, listOfLogs.length)
      val expectedAddEvt = AddMcAddrOwnership(scAddressObj1, mcMultiSigAddrBig)
      checkAddNewOwnershipEvent(expectedAddEvt, listOfLogs(0))

    }
  }


  @Test
  def testNegativeAddMultisigOwnership(): Unit = {

    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      createSenderAccount(view, initialAmount, scAddressObj1)

      // Use a valid multisig address but not corresponding to redeem script
      var cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr2, redeemScript1, listOfMcMultisigAddrSign_1)
      var data: Array[Byte] = cmdInput.encode()
      var msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      var ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Could not verify multisig address against redeemScript"))

      // Use an ill formed mc addr
      cmdInput = AddNewMultisigOwnershipCmdInput("_______ThisIsNotAnMcAddress________", redeemScript1, listOfMcMultisigAddrSign_2)
      data = cmdInput.encode()
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Could not verify multisig address against redeemScript"))


      // Use an ill formed redeem script
      cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr1, "_______ThisIsNotARedeemScript______«", listOfMcMultisigAddrSign_2)
      data = cmdInput.encode()
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Unexpected format of redeemScript"))

      // Use a wrong list of signatures address
      cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr1, redeemScript1, listOfMcMultisigAddrSign_2)
      data = cmdInput.encode()
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Invalid number of verified signatures: 0"))

      // Use too short a list of signatures address
      val seqTest = listOfMcMultisigAddrSign_1.take(1)
      cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr1, redeemScript1, seqTest)
      data = cmdInput.encode()
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Invalid number of verified signatures: 1"))

      // Use an illegal redeemScript
      cmdInput = AddNewMultisigOwnershipCmdInput(mcMultiSigAddr1, redeemScript1.replace("ae", "dd"), listOfMcMultisigAddrSign_1)
      data = cmdInput.encode()
      msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewMultisigOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Unexpected format of redeemScript"))
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

        val returnData = withGas(TestContext.process(messageProcessor, msg, view, defaultBlockContext, _))
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

        val returnData = withGas(TestContext.process(messageProcessor, msg, view, defaultBlockContext, _))
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

        val returnData = withGas(TestContext.process(messageProcessor, msg, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msg2, view, defaultBlockContext, _))
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
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Value must be zero"))

      // try removing with a value amount not null (value -1)
      msgBad = getMessage(contractAddress, BigInteger.ONE.negate(),
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode(),
        randomNonce, scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Value must be zero"))

      // should fail because input data has a trailing byte
      val badData = new Array[Byte](1)
      msgBad = getMessage(contractAddress, BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode() ++ badData,
        randomNonce, scAddressObj1
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("Wrong message data field length"))

      // should fail because sender does not exist
      msgBad = getMessage(contractAddress, BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode(),
        randomNonce, origin
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      assertTrue(ex.getMessage.contains("account does not exist"))

      // should fail because sender is not the owner
      createSenderAccount(view, initialAmount, origin)
      msgBad = getMessage(contractAddress, BigInteger.ZERO,
        BytesUtils.fromHexString(RemoveOwnershipCmd) ++ removeCmdInput.encode(),
        randomNonce, origin
      )
      ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
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
    val returnData = withGas(TestContext.process(messageProcessor, msg, stateView, defaultBlockContext, _))
    assertNotNull(returnData)
    assertArrayEquals(getOwnershipId(mcTransparentAddress), returnData)
  }

  def getAllOwnershipList(stateView: AccountStateView): Array[Byte] = {
    val msg = getMessage(contractAddress, 0, BytesUtils.fromHexString(GetListOfAllOwnershipsCmd), randomNonce)
    stateView.setupAccessList(msg)

    val (returnData, usedGas) = withGas { gas =>
      val result = TestContext.process(messageProcessor, msg, stateView, defaultBlockContext, gas)
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
      val result = TestContext.process(messageProcessor, msg, stateView, defaultBlockContext, gas)
      (result, gas.getUsedGas)
    }
    // gas consumption depends on the number of items in the list
    assertTrue(usedGas.compareTo(0) > 0)
    assertTrue(usedGas.compareTo(500000) < 0)

    assertNotNull(returnData)
    returnData
  }

  @Test
  def testNegativeAddOwnershipWithMultisigSignature(): Unit = {

    // verify we can not link the mc address with its signature applied on a multisig message (mcMultisigAddr+scAddress)
    // which is different from the ordinary message (scAddress only)
    usingView(messageProcessor) { view =>

      messageProcessor.init(view, view.getConsensusEpochNumberAsInt)

      // create sender account with some fund in it
      val initialAmount = BigInteger.valueOf(100).multiply(validWeiAmount)

      //Setting the context
      val txHash1 = Keccak256.hash("first tx")
      view.setupTxContext(txHash1, 10)

      createSenderAccount(view, initialAmount, scAddressObj1)

      // verify this is the address of one of the valid signers and the signature is valid if applied on the right message
      val messageToSign = mcMultiSigAddr1 + Keys.toChecksumAddress(Numeric.toHexString(scAddressObj1.toBytes))
      assertTrue(testIsValidOwnershipSignature(messageToSign, mcAddrStr_11, getMcSignature(mcSignatureStr_11)))

      val cmdInput = AddNewOwnershipCmdInput(mcAddrStr_11, mcSignatureStr_11)
      val data: Array[Byte] = cmdInput.encode()
      val msgBad = getMessage(
        contractAddress,
        BigInteger.ZERO,
        BytesUtils.fromHexString(AddNewOwnershipCmd) ++ data,
        randomNonce,
        scAddressObj1
      )
      val ex = intercept[ExecutionRevertedException] {
        withGas(TestContext.process(messageProcessor, msgBad, view, defaultBlockContext, _))
      }
      // the signature has been applied on a different message
      assertTrue(ex.getMessage.contains("Invalid mc signature"))
    }
  }

  def testIsValidOwnershipSignature(message: String, mcTransparentAddress: String, mcSignature: SignatureSecp256k1): Boolean = {
    // get a signature data obj for the verification
    val v_barr = getUnsignedByteArray(mcSignature.getV)
    val r_barr = padWithZeroBytes(getUnsignedByteArray(mcSignature.getR), SIGNATURE_RS_SIZE)
    val s_barr = padWithZeroBytes(getUnsignedByteArray(mcSignature.getS), SIGNATURE_RS_SIZE)

    val signatureData = new Sign.SignatureData(v_barr, r_barr, s_barr)

    // the sc address hex string used in the message to sign must have a checksum format (EIP-55: Mixed-case checksum address encoding)
    val hashedMsg = getMcHashedMsg(message)

    // verify MC message signature
    val recPubKey = Sign.signedMessageHashToKey(hashedMsg, signatureData)
    val recUncompressedPubKeyBytes = Bytes.concat(Array[Byte](0x04), Numeric.toBytesPadded(recPubKey, PUBLIC_KEY_SIZE))
    val ecpointRec = ecParameters.getCurve.decodePoint(recUncompressedPubKeyBytes)
    val recCompressedPubKeyBytes = ecpointRec.getEncoded(true)
    val mcPubkeyhash = Ripemd160Sha256Hash(recCompressedPubKeyBytes)
    val computedTaddr = toHorizenPublicKeyAddress(mcPubkeyhash, mockNetworkParams)

    computedTaddr.equals(mcTransparentAddress)
  }

}
