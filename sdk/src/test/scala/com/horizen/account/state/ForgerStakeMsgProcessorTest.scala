package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.consensus
import com.horizen.evm.{LevelDBDatabase, StateDB}
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.junit.rules.TemporaryFolder
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.math.BigInteger


@Ignore
class ForgerStakeMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
{
  var tempFolder = new TemporaryFolder

  @Before
  def setUp() : Unit = {
  }

  def getView() : AccountStateView = {
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
    val stateView = getView()

    ForgerStakeMsgProcessor.init(stateView)

    stateView.stateDb.close()

  }

  @Test
  def testCanProcess(): Unit = {
    val stateView = getView()

    val value : java.math.BigInteger = java.math.BigInteger.ONE
    val msg = new Message(null, ForgerStakeMsgProcessor.myAddress,value, value, value, value, value,value, new Array[Byte](0) )

    assertTrue(ForgerStakeMsgProcessor.canProcess(msg, stateView))

    val msgNotProcessable = new Message(null, new AddressProposition( BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea")),value, value, value, value, value,value, new Array[Byte](0) )
    assertFalse(ForgerStakeMsgProcessor.canProcess(msgNotProcessable, stateView))

    stateView.stateDb.close()
  }

  def getRandomNonce() : BigInteger = {
    val codeHash = new Array[Byte](32)
    util.Random.nextBytes(codeHash)
    new java.math.BigInteger(codeHash)
  }

  @Test
  def testAddStake(): Unit = {

    val stateView = getView()

    val dummyBigInteger: java.math.BigInteger = java.math.BigInteger.ONE
    val stakedAmount: java.math.BigInteger = new java.math.BigInteger("10000000000")
    val from : AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))
    val consensusEpochNumber : consensus.ConsensusEpochNumber = consensus.ConsensusEpochNumber@@123


    // we have to call init beforehand
    assertFalse(stateView.accountExists(ForgerStakeMsgProcessor.myAddress.address()))

    ForgerStakeMsgProcessor.init(stateView)

    assertTrue(stateView.accountExists(ForgerStakeMsgProcessor.myAddress.address()))


    val data: Array[Byte] = Bytes.concat(
      BytesUtils.fromHexString(ForgerStakeMsgProcessor.AddNewStakeCmd),
      BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788"), // blockSignProposition
      BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099"), // vrfPublicKey
      BytesUtils.fromHexString("1122334455112233445511223344551122334455")) // ownerPublicKey

    val msg = new Message(
      from,
      ForgerStakeMsgProcessor.myAddress, // to
      dummyBigInteger, // gasPrice
      dummyBigInteger, // gasFeeCap
      dummyBigInteger, // gasTipCap
      dummyBigInteger, // gasLimit
      stakedAmount,
      getRandomNonce(), // nonce
      data)

    Mockito.when(stateView.metadataStorageView.getConsensusEpochNumber).thenReturn(Some(consensusEpochNumber))

    // positive case
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))

      case result => Assert.fail(s"Wrong result: $result")
    }

    // try processing a msg with the same stake, should fail
    ForgerStakeMsgProcessor.process(msg, stateView) match {
      case res: InvalidMessage =>
        println("This is the reason: " + res.getReason.toString)
      case result => Assert.fail(s"Wrong result: $result")
    }

    val msg2 = new Message(
      from,
      ForgerStakeMsgProcessor.myAddress, // to
      dummyBigInteger, // gasPrice
      dummyBigInteger, // gasFeeCap
      dummyBigInteger, // gasTipCap
      dummyBigInteger, // gasLimit
      stakedAmount,
      getRandomNonce(), // nonce
      data)

    // try processing a msg with different stake, should succeed
    ForgerStakeMsgProcessor.process(msg2, stateView) match {
      case res: InvalidMessage => Assert.fail(s"Wrong result: $res")
      case res: ExecutionFailed => Assert.fail(s"Wrong result: $res")
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
        println("This is the returned value: " + BytesUtils.toHexString(res.returnData()))

    }

    stateView.stateDb.close()
  }

}
