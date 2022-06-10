package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.proposition.AddressProposition
import com.horizen.box.WithdrawalRequestBox
import com.horizen.fixtures.BoxFixture
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.util
import scala.util.Try

class WithdrawalMsgProcessorTest
  extends JUnitSuite
    with MockitoSugar
    with BoxFixture {

  @Before
  def setUp(): Unit = {
  }


  @Test
  def testInit(): Unit = {

    val mockStateView: AccountStateView = mock[AccountStateView]
    Mockito.when(mockStateView.addAccount(ArgumentMatchers.any[Array[Byte]], ArgumentMatchers.any[Account])).
      thenAnswer(answer => {
        Try {
          require(util.Arrays.equals(answer.getArgument(0), WithdrawalMsgProcessor.myAddress.address()))
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
    val msg = new Message(null, WithdrawalMsgProcessor.myAddress, value, value, value, value, value, value, new Array[Byte](0))
    val mockStateView: AccountStateView = mock[AccountStateView]

    assertTrue("Message for WithdrawalMsgProcessor cannot be processed", WithdrawalMsgProcessor.canProcess(msg, mockStateView))

    val msgNotProcessable = new Message(null, new AddressProposition(BytesUtils.fromHexString("35fdd51e73221f467b40946c97791a3e19799bea")), value, value, value, value, value, value, new Array[Byte](0))
    assertFalse("Message not for WithdrawalMsgProcessor can be processed", WithdrawalMsgProcessor.canProcess(msgNotProcessable, mockStateView))

  }


  @Test
  def testGetListOfWithdrawalReqs(): Unit = {

    val value: java.math.BigInteger = java.math.BigInteger.ONE
    val epochNum = 102
    val data: Array[Byte] = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.getListOfWithdrawalReqsCmdSig), Ints.toByteArray(epochNum))
    val msg = new Message(null, WithdrawalMsgProcessor.myAddress, value, value, value, value, value, value, data)
    val mockStateView: AccountStateView = mock[AccountStateView]

    val box: WithdrawalRequestBox = getWithdrawalRequestBox
    val listOfWithdrawalRequestBox = Seq(box)
    Mockito.when(mockStateView.withdrawalRequests(epochNum)).thenReturn(listOfWithdrawalRequestBox)

    WithdrawalMsgProcessor.process(msg, mockStateView) match {
      case res: ExecutionSucceeded =>
        assertTrue(res.hasReturnData)
        assertTrue(res.gasUsed() == WithdrawalMsgProcessor.gasSpentForGetListOfWithdrawalReqsCmd)
      case result => Assert.fail(s"Wrong result: $result")

    }
    val msgWithoutData = new Message(null, WithdrawalMsgProcessor.myAddress, value, value, value, value, value, value, new Array[Byte](0))

    WithdrawalMsgProcessor.process(msgWithoutData, mockStateView) match {
      case res: InvalidMessage =>
      case result => Assert.fail(s"Wrong result: $result")

    }


  }

  //  @Test
  //  def testProcessFailure(): Unit = {
  //
  //    val gasValue : java.math.BigInteger = java.math.BigInteger.ONE
  //    val nonce : java.math.BigInteger = java.math.BigInteger.ONE
  //    val invalidValueForTxInZenny : java.math.BigInteger = java.math.BigInteger.valueOf(1000000001L) //1 zenny and 1 wei
  //    val msg = new Message(null, WithdrawalMsgProcessor.myAddress, gasValue, gasValue, gasValue, gasValue, invalidValueForTxInZenny,nonce, new Array[Byte](0) )
  //    val mockStateView: AccountStateView = mock[AccountStateView]
  //
  //    val result = WithdrawalMsgProcessor.process(msg, mockStateView)
  //    assertTrue("Wrong result type for invalidValueForTxInZenny", result.isInstanceOf[ExecutionFailed])
  //
  //
  //  }

}
