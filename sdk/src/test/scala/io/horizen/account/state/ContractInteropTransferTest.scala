package io.horizen.account.state

import io.horizen.evm._
import io.horizen.utils.BytesUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger

class ContractInteropTransferTest extends ContractInteropTestBase {
  override val processorToTest: NativeSmartContractMsgProcessor = NativeTestContract

  private object NativeTestContract extends NativeSmartContractMsgProcessor {
    override val contractAddress: Address = new Address("0x00000000000000000000000000000000deadbeef")
    override val contractCode: Array[Byte] = Keccak256.hash("NativeTestContract")

    override def process(
        invocation: Invocation,
        view: BaseAccountStateView,
        context: ExecutionContext
    ): Array[Byte] = {
      // accept incoming value transfers
      if (invocation.value.signum() > 0) {
        view.subBalance(invocation.caller, invocation.value)
        view.addBalance(contractAddress, invocation.value)
      }
      // forward value to another account if an address is given
      if (invocation.input.length == 20) {
        // read target contract address from input
        val evmContractAddress = new Address(invocation.input)
        // execute nested call to EVM contract
        context.execute(invocation.call(evmContractAddress, invocation.value, Array.emptyByteArray, 10000))
      }
      Array.emptyByteArray
    }
  }

  val transferValue: BigInteger = BigInteger.valueOf(100000)

  @Test
  def testTransferToNativeContract(): Unit = {
    // transfer some value to the native contract directly: EOA => native contract
    transition(getMessage(NativeTestContract.contractAddress, value = transferValue))
    assertEquals(initialBalance.subtract(transferValue), stateView.getBalance(origin))
    assertEquals(transferValue, stateView.getBalance(NativeTestContract.contractAddress))
  }

  @Test
  def testTransferToEvmContract(): Unit = {
    // deploy an EVM-based contract
    val address = deploy(ContractInteropTestBase.nativeInteropContractCode)
    // transfer some value to the EVM contract directly: EOA => EVM contract
    transition(getMessage(address, value = transferValue))
    assertEquals(initialBalance.subtract(transferValue), stateView.getBalance(origin))
    assertEquals(transferValue, stateView.getBalance(address))
  }

  @Test
  def testTransferFromNativeToEvmContract(): Unit = {
    // deploy an EVM-based contract
    val address = deploy(ContractInteropTestBase.nativeInteropContractCode)
    // call the native contract with some value and make it forward the amount to the EVM contract
    transition(getMessage(NativeTestContract.contractAddress, value = transferValue, data = address.toBytes))
    // origin lost the transferred amount
    assertEquals(initialBalance.subtract(transferValue), stateView.getBalance(origin))
    // the native contract does not have any balance, everything should have been forwarded
    assertEquals(BigInteger.ZERO, stateView.getBalance(NativeTestContract.contractAddress))
    // the transferred amount reached the EVM contract
    assertEquals(transferValue, stateView.getBalance(address))
  }

  @Test
  def testTransferFromEvmToNativeContract(): Unit = {
    // deploy an EVM-based contract
    val address = deploy(ContractInteropTestBase.nativeInteropContractCode)
    // call the EVM contract with some value and make it forward the amount to the native contract
    val calldata = BytesUtils.fromHexString("7d286e48") ++ new Array[Byte](12) ++ NativeTestContract.contractAddress.toBytes
    transition(getMessage(address, value = transferValue, data = calldata))
    // origin lost the transferred amount
    assertEquals(initialBalance.subtract(transferValue), stateView.getBalance(origin))
    // the EVM contract does not have any balance, everything should have been forwarded
    assertEquals(BigInteger.ZERO, stateView.getBalance(address))
    // the transferred amount reached the native contract
    assertEquals(transferValue, stateView.getBalance(NativeTestContract.contractAddress))
  }

  @Test
  def testTransferFromNativeToEoa(): Unit = {
    // call the native contract with some value and make it forward the amount to the EOA
    transition(getMessage(NativeTestContract.contractAddress, value = transferValue, data = origin2.toBytes))
    // origin lost the transferred amount
    assertEquals(initialBalance.subtract(transferValue), stateView.getBalance(origin))
    // the native contract does not have any balance, everything should have been forwarded
    assertEquals(BigInteger.ZERO, stateView.getBalance(NativeTestContract.contractAddress))
    // the transferred amount reached the EVM contract
    assertEquals(transferValue, stateView.getBalance(origin2))
  }
}

