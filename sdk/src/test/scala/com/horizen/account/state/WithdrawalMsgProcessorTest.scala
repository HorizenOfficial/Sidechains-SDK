package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.account.api.http.ZenWeiConverter
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.evm.{LevelDBDatabase, StateDB}
import com.horizen.fixtures.BoxFixture
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.{BytesUtils, WithdrawalEpochInfo}
import org.junit.Assert._
import org.junit._
import org.junit.rules.TemporaryFolder
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.util
import scala.util.{Random, Try}


class WithdrawalMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with BoxFixture {

  val fakeAddress = new AddressProposition(BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea"))
  var tempFolder = new TemporaryFolder

  @Before
  def setUp(): Unit = {
  }

  def getView(): AccountStateView = {
    tempFolder.create()
    val databaseFolder = tempFolder.newFolder("evm-db" + Math.random())
    val hashNull = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")
    val db = new LevelDBDatabase(databaseFolder.getAbsolutePath())
    val messageProcessors: Seq[MessageProcessor] = Seq()
    val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb: StateDB = new StateDB(db, hashNull)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }


  @Test
  def testInit(): Unit = {

    val mockStateView: AccountStateView = mock[AccountStateView]
    Mockito.when(mockStateView.addAccount(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[Array[Byte]])).
      thenAnswer(answer => {
        Try {
          require(util.Arrays.equals(answer.getArgument(0), WithdrawalMsgProcessor.fakeSmartContractAddress.address()))
          val account: Account = answer.getArgument(1).asInstanceOf[Account]
          require(account.nonce == 0)
          require(account.balance == 0)
          require(account.codeHash != null)
          require(account.storageRoot != null)
          mockStateView
        }
      })

    WithdrawalMsgProcessor.init(mockStateView)

  }

  @Test
  def testCanProcess(): Unit = {

    val value: java.math.BigInteger = java.math.BigInteger.ONE
    val msg = new Message(null, WithdrawalMsgProcessor.fakeSmartContractAddress, value, value, value, value, value, value, new Array[Byte](0))
    val mockStateView: AccountStateView = mock[AccountStateView]

    assertTrue("Message for WithdrawalMsgProcessor cannot be processed", WithdrawalMsgProcessor.canProcess(msg, mockStateView))

    val msgNotProcessable = new Message(null, fakeAddress, value, value, value, value, value, value, new Array[Byte](0))
    assertFalse("Message not for WithdrawalMsgProcessor can be processed", WithdrawalMsgProcessor.canProcess(msgNotProcessable, mockStateView))

  }


  @Test
  def testProcess(): Unit = {
    val gasValue: java.math.BigInteger = java.math.BigInteger.ONE
    val nonce: java.math.BigInteger = java.math.BigInteger.ONE
    val value: java.math.BigInteger = java.math.BigInteger.valueOf(1000000000L) //1 zenny and 1 wei

    val msgForWrongProcessor = new Message(null, fakeAddress, gasValue, gasValue, gasValue, gasValue, value, nonce, new Array[Byte](0))
    val mockStateView: AccountStateView = mock[AccountStateView]
    assertEquals("msgForWrongProcessor processing should result in InvalidMessage", classOf[InvalidMessage], WithdrawalMsgProcessor.process(msgForWrongProcessor, mockStateView).getClass)

    val data = BytesUtils.fromHexString("99")
    val msgWithWrongFunctionCall = new Message(null, WithdrawalMsgProcessor.fakeSmartContractAddress, gasValue, gasValue, gasValue, gasValue, value, nonce, data)
    assertEquals("msgWithWrongFunctionCall processing should result in ExecutionFailed", classOf[ExecutionFailed], WithdrawalMsgProcessor.process(msgWithWrongFunctionCall, mockStateView).getClass)

    val mockMsg = mock[Message]
    Mockito.when(mockMsg.getTo).thenReturn(WithdrawalMsgProcessor.fakeSmartContractAddress)
    val expException = new RuntimeException()
    Mockito.when(mockMsg.getData).thenThrow(expException)
    val res = WithdrawalMsgProcessor.process(mockMsg, mockStateView)
    assertEquals("msgWithWrongFunctionCall processing should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(expException, res.asInstanceOf[ExecutionFailed].getReason)

  }


  @Test
  def testAddWithdrawalRequestFailures(): Unit = {
    val mockStateView = mock[AccountStateView]

    val from: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))

    val withdrawalAmountUnderDustThreshold: java.math.BigInteger = ZenWeiConverter.convertZenniesToWei(13)
    val mcAddr = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    val data: Array[Byte] = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.addNewWithdrawalReqCmdSig),
      mcAddr.bytes())
    val gas = java.math.BigInteger.ONE
    val msg = new Message(from, WithdrawalMsgProcessor.fakeSmartContractAddress, gas, gas, gas, gas, withdrawalAmountUnderDustThreshold, java.math.BigInteger.valueOf(234), data)


    Mockito.when(mockStateView.getBalance(from.address())).thenReturn(ZenWeiConverter.convertZenniesToWei(1300))
    val res = WithdrawalMsgProcessor.process(msg, mockStateView)
    assertEquals("Withdrawal request under dust threshold processing should result in ExecutionFailed", classOf[ExecutionFailed], res.getClass)
    assertEquals(classOf[IllegalArgumentException], res.asInstanceOf[ExecutionFailed].getReason.getClass)


  }

    @Test
  def testAddWithdrawalRequestIntegration(): Unit = {
    // Step 1: setup state view
    val stateView = getView()
    WithdrawalMsgProcessor.init(stateView)
    val epochNum = 102
    Mockito.when(stateView.metadataStorageView.getWithdrawalEpochInfo).thenReturn(Some(WithdrawalEpochInfo(epochNum, 1)))

    // Step 2: setup balance
    val from: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))
    stateView.addBalance(from.address(), ZenWeiConverter.convertZenniesToWei(1300))

    // Step 3: setup message
    val withdrawalAmount: java.math.BigInteger = ZenWeiConverter.convertZenniesToWei(123)
    val mcAddr = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
    val data: Array[Byte] = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.addNewWithdrawalReqCmdSig),
      mcAddr.bytes())
    val gas = java.math.BigInteger.ONE
    val msg = new Message(from, WithdrawalMsgProcessor.fakeSmartContractAddress, gas, gas, gas, gas, withdrawalAmount, java.math.BigInteger.valueOf(234), data)

    // Step 4: call process
    val res = WithdrawalMsgProcessor.process(msg, stateView)

    assertEquals("Wrong result type", classOf[ExecutionSucceeded], res.getClass)
    assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
    val wtInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
    val wt = WithdrawalRequestSerializer.parseBytes(wtInBytes)
    assertEquals("Wrong destination address", mcAddr, wt.proposition)
    assertEquals("Wrong amount", withdrawalAmount, wt.value)
  }

  @Ignore
  @Test
  def testGetListOfWithdrawalReqs(): Unit = {

        val withdrawalAmount: java.math.BigInteger = ZenWeiConverter.convertZenniesToWei(13)
        val epochNum = 102
        val mcAddr = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
        val data: Array[Byte] = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.getListOfWithdrawalReqsCmdSig),
          mcAddr.bytes())
        val gas = java.math.BigInteger.ONE
        val msg = new Message(null, WithdrawalMsgProcessor.myAddress, gas, gas, gas, gas, withdrawalAmount, 234, data)
        val mockStateView: AccountStateView = mock[AccountStateView]

        Mockito.when(mockStateView.withdrawalRequests(epochNum)).thenReturn(listOfWithdrawalRequestBox)

        val res = WithdrawalMsgProcessor.process(msg, mockStateView)
        assertTrue("Wrong result", res.isInstanceOf[ExecutionSucceeded])
        assertTrue("Missing withdrawal request data", res.asInstanceOf[ExecutionSucceeded].hasReturnData)
        val wtInBytes = res.asInstanceOf[ExecutionSucceeded].returnData()
        val wt = WithdrawalRequestSerializer.parseBytes(wtInBytes)
        assertEquals("Wrong destination address", mcAddr, wt.proposition)
        assertEquals("Wrong amount", withdrawalAmount, wt.value)

        val msgWithoutData = new Message(null, WithdrawalMsgProcessor.myAddress, value, value, value, value, value, value, new Array[Byte](0))

        WithdrawalMsgProcessor.process(msgWithoutData, mockStateView) match {
          case res: InvalidMessage =>
          case result => Assert.fail(s"Wrong result: $result")

        }
  }


}
