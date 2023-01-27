package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils.{Account, FeeUtils}
import com.horizen.evm.utils.Hash
import com.horizen.evm.{MemoryDatabase, StateDB}
import com.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert.assertEquals
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{EventEncoder, FunctionReturnDecoder, TypeReference}

import java.math.BigInteger
import java.util.Optional
import scala.language.implicitConversions
import scala.util.{Failure, Random, Success, Try}

trait MessageProcessorFixture extends ClosableResourceHandler {
  // simplifies using BigIntegers within the tests
  implicit def longToBigInteger(x: Long): BigInteger = BigInteger.valueOf(x)

  val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
  val hashNull: Array[Byte] = Array.fill(32)(0)
  val origin: Array[Byte] = randomAddress
  val defaultBlockContext = new BlockContext(Array.fill(20)(0), 0, 0, FeeUtils.GAS_LIMIT, 0, 0, 0, 1)

  def randomBytes(n: Int): Array[Byte] = {
    val bytes = new Array[Byte](n)
    Random.nextBytes(bytes)
    bytes
  }

  def randomU256: BigInteger = new BigInteger(randomBytes(32))

  def randomHash: Array[Byte] = randomBytes(32)

  def randomAddress: Array[Byte] = randomBytes(Account.ADDRESS_SIZE)

  def usingView(processors: Seq[MessageProcessor])(fun: AccountStateView => Unit): Unit = {
    using(new MemoryDatabase()) { db =>
      val stateDb = new StateDB(db, hashNull)
      using(new AccountStateView(metadataStorageView, stateDb, processors))(fun)
    }
  }

  def usingView(processor: MessageProcessor)(fun: AccountStateView => Unit): Unit = {
    usingView(Seq(processor))(fun)
  }

  def usingView(fun: AccountStateView => Unit): Unit = {
    usingView(Seq.empty)(fun)
  }

  def getMessage(
      to: Array[Byte],
      value: BigInteger = BigInteger.ZERO,
      data: Array[Byte] = Array.emptyByteArray,
      nonce: BigInteger = BigInteger.ZERO,
      from: Array[Byte] = null
  ): Message = {
    val gasPrice = BigInteger.ZERO
    val gasFeeCap = BigInteger.valueOf(1000001)
    val gasTipCap = BigInteger.ZERO
    val gasLimit = BigInteger.valueOf(500000)
    new Message(
      if (from == null)
        Optional.of(new AddressProposition(origin))
      else
        Optional.of(new AddressProposition(from)),
      if (to == null)
        Optional.empty()
      else
        Optional.of(new AddressProposition(to)),
      gasPrice,
      gasFeeCap,
      gasTipCap,
      gasLimit,
      value,
      nonce,
      data,
      false
    )
  }

  /**
   * Creates a large temporary gas pool and passes it into the given function.
   */
  def withGas[A](fun: GasPool => A, gasLimit: BigInteger = 1000000): A = {
    fun(new GasPool(gasLimit))
  }

  /**
   * Creates a large temporary gas pool and verifies the amount of total gas consumed.
   */
  def assertGas[A](expectedGas: BigInteger, enforce: Boolean = true)(fun: GasPool => A): A = {
    withGas { gas =>
      try {
        fun(gas)
      } finally {
        if (enforce) {
          assertEquals("Unexpected gas consumption", expectedGas, gas.getUsedGas)
        } else {
          println("consumed gas: " + gas.getUsedGas)
          if (expectedGas != gas.getUsedGas)
            println(" mismatch here, expected is: " + expectedGas)
        }
      }
    }
  }

  def assertGas(
      expectedGas: BigInteger,
      msg: Message,
      view: AccountStateView,
      processor: MessageProcessor,
      ctx: BlockContext,
  ): Array[Byte] = {
    // DEBUG: set to false to print gas usage and disable assertion
    val enforce = true
    view.setupAccessList(msg)
    val gas = new GasPool(1000000)
    val result = Try.apply(processor.process(msg, view, gas, ctx))
    // verify gas usage in case of success or revert, in any other case the consumption does not matter:
    // - in case of ExecutionFailedException all available gas will be burned anyway
    // - any other exception will invalidate the message/transaction/block or in case of forging
    //    will drop the tx from the new block
    result match {
      case Success(_) | Failure(_: ExecutionRevertedException) =>
        if (enforce) {
          assertEquals("Unexpected gas consumption", expectedGas, gas.getUsedGas)
        } else {
          println("consumed gas: " + gas.getUsedGas)
          if (expectedGas != gas.getUsedGas)
            println(" mismatch here, expected is: " + expectedGas)
        }
    }
    // return result or rethrow any exception
    result.get
  }

  def getEventSignature(eventABISignature: String): Array[Byte] =
    org.web3j.utils.Numeric.hexStringToByteArray(EventEncoder.buildEventSignature(eventABISignature))

  def decodeEventTopic[T <: Type[_]](topic: Hash, ref: TypeReference[T]): Type[_] =
    FunctionReturnDecoder.decodeIndexedValue(BytesUtils.toHexString(topic.toBytes), ref)
}
