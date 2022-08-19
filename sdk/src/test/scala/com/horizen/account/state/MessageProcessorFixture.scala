package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils.Account
import com.horizen.evm.utils.Hash
import com.horizen.evm.{MemoryDatabase, StateDB}
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert.assertEquals
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{EventEncoder, FunctionReturnDecoder, TypeReference}

import java.math.BigInteger
import scala.language.implicitConversions
import scala.util.Random

trait MessageProcessorFixture extends ClosableResourceHandler {
  val mcAddr = new MCPublicKeyHashProposition(randomBytes(20))
  val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
  val hashNull: Array[Byte] =
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")

  // simplifies using BigIntegers within the tests
  implicit def longToBigInteger(x: Long): BigInteger = BigInteger.valueOf(x)

  def randomBytes(n: Int): Array[Byte] = {
    val bytes = new Array[Byte](n)
    Random.nextBytes(bytes)
    bytes
  }

  def randomU256: BigInteger = new BigInteger(randomBytes(32))

  def randomHash: Array[Byte] = randomBytes(32)

  def randomAddress: AddressProposition = new AddressProposition(randomBytes(Account.ADDRESS_SIZE))

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

  def getMessage(destAddress: AddressProposition, value: BigInteger, data: Array[Byte]): Message = {
    val gasPrice = BigInteger.ZERO
    val gasLimit = BigInteger.valueOf(1000000)
    val nonce = BigInteger.ZERO
    val from = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))
    new Message(from, destAddress, gasPrice, gasPrice, gasPrice, gasLimit, value, nonce, data)
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
