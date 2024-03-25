package io.horizen.account.state

import io.horizen.account.AccountFixture
import io.horizen.account.fork.GasFeeFork.DefaultGasFeeFork
import io.horizen.account.storage.AccountStateMetadataStorageView
import io.horizen.consensus.intToConsensusEpochNumber
import io.horizen.evm._
import io.horizen.utils.{BytesUtils, ClosableResourceHandler}
import org.junit.Assert.assertEquals
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.abi.datatypes.Type
import org.web3j.abi.{EventEncoder, FunctionReturnDecoder, TypeReference}

import java.math.BigInteger
import java.util.Optional
import scala.language.implicitConversions
import scala.util.Try

trait MessageProcessorFixture extends AccountFixture with ClosableResourceHandler {
  val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
  Mockito.when(metadataStorageView.getConsensusEpochNumber).thenReturn(Option(intToConsensusEpochNumber(33)))


  val origin: Address = randomAddress
  val origin2: Address = randomAddress
  val defaultBlockContext =
    new BlockContext(Address.ZERO, 0, 0, DefaultGasFeeFork.blockGasLimit, 0, 33, 0, 1, MockedHistoryBlockHashProvider, Hash.ZERO)
  def usingView(processors: Seq[MessageProcessor])(fun: AccountStateView => Unit): Unit = {
    using(new MemoryDatabase()) { db =>
      val stateDb = new StateDB(db, Hash.ZERO)
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
    val gasLimit = BigInteger.valueOf(500000)
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
  def withGas[A](fun: GasPool => A, gasLimit: BigInteger = 10000000): A = {
    fun(new GasPool(gasLimit))
  }

  /**
   * Creates a large temporary gas pool and verifies the amount of total gas consumed.
   */
  def assertGas(
      expectedGas: BigInteger,
      msg: Message,
      view: AccountStateView,
      processor: MessageProcessor,
      ctx: BlockContext,
  ): Array[Byte] = {
    view.setupAccessList(msg, ctx.forgerAddress, new ForkRules(true))
    val gas = new GasPool(1000000000)
    val result = Try.apply(TestContext.process(processor, msg, view, ctx, gas))
    assertEquals("Unexpected gas consumption", expectedGas, gas.getUsedGas)
    // return result or rethrow any exception
    result.get
  }

  /**
   * Creates a large temporary gas pool and verifies the amount of total gas consumed.
   * It uses StateTransition instead of TestContext in order to allow calls between smart contracts.
   */
  def assertGasInterop(
                 expectedGas: BigInteger,
                 msg: Message,
                 view: AccountStateView,
                 processors: Seq[MessageProcessor],
                 ctx: BlockContext,
               ): Array[Byte] = {
    view.setupAccessList(msg, ctx.forgerAddress, new ForkRules(true))
    val gas = new GasPool(1000000000)
    val transition = new StateTransition(view, processors, gas, ctx, msg)
    val result = Try.apply(transition.execute(Invocation.fromMessage(msg, gas)))
    assertEquals("Unexpected gas consumption", expectedGas, gas.getUsedGas)
    // return result or rethrow any exception
    result.get
  }


  def getEventSignature(eventABISignature: String): Array[Byte] =
    org.web3j.utils.Numeric.hexStringToByteArray(EventEncoder.buildEventSignature(eventABISignature))

  def decodeEventTopic[T <: Type[_]](topic: Hash, ref: TypeReference[T]): Type[_] =
    FunctionReturnDecoder.decodeIndexedValue(BytesUtils.toHexString(topic.toBytes), ref)

  def createSenderAccount(view: AccountStateView, amount: BigInteger = BigInteger.ZERO, inAddress: Address = origin): Unit = {
    if (!view.accountExists(inAddress)) {
      view.addAccount(inAddress, randomHash)

      if (amount.signum() == 1) {
        view.addBalance(inAddress, amount)
      }
    }
  }
}
