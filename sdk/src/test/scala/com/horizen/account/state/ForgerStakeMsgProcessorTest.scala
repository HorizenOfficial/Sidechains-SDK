package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit._
import org.mockito._
import org.scalatestplus.junit.JUnitSuite
import org.scalatestplus.mockito._

import java.util
import scala.util.Try

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


}
