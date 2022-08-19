package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils.Account
import com.horizen.evm.utils.Hash
import com.horizen.evm.{MemoryDatabase, StateDB}
import com.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert.assertEquals
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{EventEncoder, FunctionReturnDecoder, TypeReference}

import java.math.BigInteger
import scala.language.implicitConversions
import scala.util.Random

trait MessageProcessorFixture extends ClosableResourceHandler {
  val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
  val hashNull: Array[Byte] = Array.fill(32)(0)
  val origin: Array[Byte] = randomAddress

  // simplifies using BigIntegers within the tests
  implicit def longToBigInteger(x: Long): BigInteger = BigInteger.valueOf(x)

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
      nonce: BigInteger = BigInteger.ZERO): Message = {
    val gasPrice = BigInteger.ZERO
    val gasLimit = BigInteger.valueOf(1000000)
    new Message(
      new AddressProposition(origin),
      if (to == null) null else new AddressProposition(to),
      gasPrice,
      gasPrice,
      gasPrice,
      gasLimit,
      value,
      nonce,
      data)
  }

  /**
   * Creates a large temporary gas pool and passes it into the given function.
   */
  def withGas[A](fun: GasPool => A): A = {
    fun(new GasPool(BigInteger.valueOf(1000000)))
  }

  /**
   * Creates a large temporary gas pool and verifies the amount of total gas consumed.
   */
  def assertGas[A](expectedGas: BigInteger = BigInteger.ZERO)(fun: GasPool => A): A = {
    withGas { gas =>
      try {
        fun(gas)
      } finally {
        assertEquals("Unexpected gas consumption", expectedGas, gas.getUsedGas)
      }
    }
  }

  def getEventSignature(eventABISignature: String): Array[Byte] =
    org.web3j.utils.Numeric.hexStringToByteArray(EventEncoder.buildEventSignature(eventABISignature))

  def decodeEventTopic[T <: Type[_]](topic: Hash, ref: TypeReference[T]): Type[_] =
    FunctionReturnDecoder.decodeIndexedValue(BytesUtils.toHexString(topic.toBytes), ref)
}
