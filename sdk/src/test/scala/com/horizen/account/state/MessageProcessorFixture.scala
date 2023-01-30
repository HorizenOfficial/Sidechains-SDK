package com.horizen.account.state

import com.horizen.account.AccountFixture
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.utils.FeeUtils
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.evm.{MemoryDatabase, StateDB}
import com.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert.assertEquals
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{EventEncoder, FunctionReturnDecoder, TypeReference}

import java.math.BigInteger
import java.util.Optional

trait MessageProcessorFixture extends AccountFixture with ClosableResourceHandler {
  val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
  val origin: Address = randomAddress
  val defaultBlockContext = new BlockContext(Address.ZERO, 0, 0, FeeUtils.GAS_LIMIT, 0, 0, 0, 1)

  def usingView(processors: Seq[MessageProcessor])(fun: AccountStateView => Unit): Unit = {
    using(new MemoryDatabase()) { db =>
      val stateDb = new StateDB(db, Hash.ZERO.toBytes)
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
      to: Address,
      value: BigInteger = BigInteger.ZERO,
      data: Array[Byte] = Array.emptyByteArray,
      nonce: BigInteger = BigInteger.ZERO,
      from: Address = origin
  ): Message = {
    val gasPrice = BigInteger.ZERO
    val gasFeeCap = BigInteger.valueOf(1000001)
    val gasTipCap = BigInteger.ZERO
    val gasLimit = BigInteger.valueOf(1000000)
    new Message(
      from,
      Optional.ofNullable(to),
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

  def getEventSignature(eventABISignature: String): Array[Byte] =
    org.web3j.utils.Numeric.hexStringToByteArray(EventEncoder.buildEventSignature(eventABISignature))

  def decodeEventTopic[T <: Type[_]](topic: Hash, ref: TypeReference[T]): Type[_] =
    FunctionReturnDecoder.decodeIndexedValue(BytesUtils.toHexString(topic.toBytes), ref)
}
