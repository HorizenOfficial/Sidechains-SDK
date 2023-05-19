package io.horizen.account.api.rpc.service

import io.horizen.account.api.rpc.handler.RpcException
import io.horizen.account.api.rpc.utils.RpcCode
import org.junit.Assert.assertEquals
import org.junit.{Before, Test}
import org.mockito.Mockito

class EthServiceForSeederTest extends EthServiceTest {

  @Before
  override def setUp(): Unit = {
    super.setUp()
    Mockito.doAnswer(_ => false).when(networkParams).isHandlingTransactionsEnabled
  }

  @Test
  override def eth_sendRawTransaction(): Unit = {
    val validRawTx =
      "0xf86f82674685031b1de8ce83019a289452cceccf519c4575a3cbf3bff5effa5e9181cec4880b9f5bd224727a808025a0cdf8d5eb0f83dff14c87aee3ff7cb373780520117fe735de78bc5eb25e700beba00b7120958d87d26425fd70d1e4c2bfb4022392417bc567887eafd5d7da09ccdf"
    val validCases = Table(
      "Transaction",
      validRawTx
    )


    forAll(validCases) { input =>
      val exc = intercept[RpcException] {
        rpc("eth_sendRawTransaction", input)
      }
      assertEquals("Wrong exception", RpcCode.ActionNotAllowed.code, exc.error.code)
    }

  }

  @Test
  override def eth_sendTransaction(): Unit = {
    val validCases = Table(
      "Transaction parameters",
       Map(
          "from" -> senderWithSecret,
          "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
          "gas" -> "0x76c0",
          "gasPrice" -> "0x9184e72a000",
          "value" -> "0x9184e72a",
          "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
          "nonce" -> "0x1",
        ),
        "\"0x052ad22aed5bce6bbbf38d1da630396178b6667dda7bbf573b90d77dd8bf39e4\""
    )


    forAll(validCases) { tx =>
      val exc = intercept[RpcException] {
        rpc("eth_sendTransaction", tx)
      }
      assertEquals("Wrong exception", RpcCode.ActionNotAllowed.code, exc.error.code)
    }
  }

  @Test
  override def eth_signTransaction(): Unit = {
    val validCases = Table(
      "Transaction parameters",
        Map(
          "from" -> senderWithSecret,
          "to" -> "0x52cceccf519c4575a3cbf3bff5effa5e9181cec4",
          "gas" -> "0x76c0",
          "gasPrice" -> "0x9184e72a000",
          "value" -> "0x9184e72a",
          "data" -> "0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675",
          "nonce" -> "0x1",
        )
    )

    forAll(validCases) { input =>
      val exc = intercept[RpcException] {
        rpc("eth_signTransaction", input)
      }
      assertEquals("Wrong exception", RpcCode.ActionNotAllowed.code, exc.error.code)
    }

  }

  @Test
  override def eth_sign(): Unit = {
    val validCases = Table(
      ("Sender", "message"),
      (
        senderWithSecret,
        "0xdeadbeef",
      )
    )


    forAll(validCases) { (sender, message) =>
      val exc = intercept[RpcException] {
        rpc("eth_sign", sender, message)
      }
      assertEquals("Wrong exception", RpcCode.ActionNotAllowed.code, exc.error.code)
    }

  }



}
