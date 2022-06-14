package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.proposition.AddressProposition
import com.horizen.consensus
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.util
import scala.util.{Success, Try}

class ForgerStakeMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
{

  @Before
  def setUp() : Unit = {
  }


  @Test
  def testInit(): Unit = {

    val mockStateView: AccountStateView = mock[AccountStateView]
    Mockito.when(mockStateView.addAccount(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[Account])).
      thenAnswer(answer => {
        Try {
          require(util.Arrays.equals(answer.getArgument(0), ForgerStakeMsgProcessor.myAddress.address()))
          val account: Account = answer.getArgument(1).asInstanceOf[Account]
          require(account.nonce == 0)
          require(account.balance == 0)
          require(account.codeHash != null)
          require(account.storageRoot != null)
          mockStateView
        }
      })

    ForgerStakeMsgProcessor.init(mockStateView)

  }

  @Test
  def testCanProcess(): Unit = {

    val value : java.math.BigInteger = java.math.BigInteger.ONE
    val msg = new Message(null, ForgerStakeMsgProcessor.myAddress,value, value, value, value, value,value, new Array[Byte](0) )
    val mockStateView: AccountStateView = mock[AccountStateView]

    assertTrue(ForgerStakeMsgProcessor.canProcess(msg, mockStateView))

    val msgNotProcessable = new Message(null, new AddressProposition( BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea")),value, value, value, value, value,value, new Array[Byte](0) )
    assertFalse(ForgerStakeMsgProcessor.canProcess(msgNotProcessable, mockStateView))


  }

  @Test
  def testAddStake(): Unit = {

    val dummyBigInteger: java.math.BigInteger = java.math.BigInteger.ONE
    val stakedAmount: java.math.BigInteger = new java.math.BigInteger("10000000000")
    val from : AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))
    val consensusEpochNumber : consensus.ConsensusEpochNumber = consensus.ConsensusEpochNumber@@123

    val mockStateView: AccountStateView = mock[AccountStateView]

    val data: Array[Byte] = Bytes.concat(
      BytesUtils.fromHexString(ForgerStakeMsgProcessor.AddNewStakeCmd),
      BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788"),
      BytesUtils.fromHexString("aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099aabbccddeeff0099"),
      BytesUtils.fromHexString("1122334455112233445511223344551122334455"))

    val msg = new Message(from, ForgerStakeMsgProcessor.myAddress, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger,
      stakedAmount, dummyBigInteger, data)

    Mockito.when(mockStateView.getConsensusEpochNumber).thenReturn(Some(consensusEpochNumber))

    // set returned value to empty record (no stakes yet)
    Mockito.when(mockStateView.getAccountStorageBytes(
      ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[Array[Byte]])).thenReturn(Success(new Array[Byte](0)))

    ForgerStakeMsgProcessor.process(msg, mockStateView) match {
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == ForgerStakeMsgProcessor.AddNewStakeGasPaidValue)
      case result => Assert.fail(s"Wrong result: $result")
    }

    /*
    val msgWithoutData = new Message(null, ForgerStakeMsgProcessor.myAddress, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, dummyBigInteger, new Array[Byte](0))

    ForgerStakeMsgProcessor.process(msgWithoutData, mockStateView) match {
      case res: InvalidMessage =>
      case result => Assert.fail(s"Wrong result: $result")

    }

     */


  }

}
