package com.horizen.account.state

import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
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
  val mcAddr = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
  val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
  val hashNull: Array[Byte] =
    BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")

  // simplifies using BigIntegers within the tests
  implicit def longToBigInteger(x: Long): BigInteger = BigInteger.valueOf(x)

  def usingView(processor: MessageProcessor)(fun: AccountStateView => Unit): Unit = {
    using(new MemoryDatabase()) { db =>
      val stateDb = new StateDB(db, hashNull)
      using(new AccountStateView(metadataStorageView, stateDb, Seq(processor)))(fun)
    }
  }

  def getMessage(destContractAddress: AddressProposition, value: BigInteger, data: Array[Byte]): Message = {
    val gas = BigInteger.ZERO
    val gasLimit = BigInteger.valueOf(1000000)
    val nonce = BigInteger.ZERO
    val from = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))
    new Message(from, destContractAddress, gas, gas, gas, gasLimit, value, nonce, data)
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
      val ret = fun(gas)
      assertEquals("Unexpected gas consumption", expectedGas, gas.getUsedGas)
      ret
    }
  }

  def getEventSignature(eventABISignature: String): Array[Byte] =
    org.web3j.utils.Numeric.hexStringToByteArray(EventEncoder.buildEventSignature(eventABISignature))

  def decodeEventTopic[T <: Type[_]](topic: Hash, ref: TypeReference[T]): Type[_] =
    FunctionReturnDecoder.decodeIndexedValue(BytesUtils.toHexString(topic.toBytes), ref)

}
