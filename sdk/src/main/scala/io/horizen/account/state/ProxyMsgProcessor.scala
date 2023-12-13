package io.horizen.account.state

import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.fork.ContractInteroperabilityFork
import io.horizen.account.state.ProxyMsgProcessor._
import io.horizen.account.state.events.ProxyInvocation
import io.horizen.account.utils.WellKnownAddresses.PROXY_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.params.{MainNetParams, NetworkParams, RegTestParams}
import io.horizen.utils.BytesUtils
import org.web3j.utils.Numeric
import sparkz.crypto.hash.Keccak256

/*
 This Message Processor is used for testing invocations of EVM smart contracts from native smart contracts.
 It can only be used when in regtest.
 */
case class ProxyMsgProcessor(params: NetworkParams) extends NativeSmartContractWithFork {

  override val contractAddress: Address = PROXY_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ProxySmartContractCode")

  override def canProcess(invocation: Invocation, view: BaseAccountStateView, consensusEpochNumber: Int): Boolean = {
    params.isInstanceOf[RegTestParams] && super.canProcess(invocation, view, consensusEpochNumber)
  }

  override def isForkActive(consensusEpochNumber: Int): Boolean = {
    ContractInteroperabilityFork.get(consensusEpochNumber).active
  }

  def doInvokeSmartContractStaticCallCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    doInvokeSmartContractCmd(invocation, view, context, readOnly = true)
  }

  def doInvokeSmartContractCallCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    doInvokeSmartContractCmd(invocation, view, context, readOnly = false)
  }

  private def doInvokeSmartContractCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext, readOnly : Boolean): Array[Byte] = {
    log.debug(s"Entering with invocation: $invocation")

    val value = invocation.value

    // check that invocation.value is greater or equal than zero
    if (value.signum() < 0) {
      throw new ExecutionRevertedException("Value must not be zero")
    }

    // check that sender account exists
    if (!view.accountExists(invocation.caller)) {
      throw new ExecutionRevertedException(s"Sender account does not exist: ${invocation.caller}")
    }

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = InvokeSmartContractCmdInputDecoder.decode(inputParams)
    val contractAddress = cmdInput.contractAddress
    val data = cmdInput.dataStr

    if (view.isEoaAccount(contractAddress)) {
      throw new ExecutionRevertedException(s"smart contract address is an EOA")
    }

    val dataBytes = Numeric.hexStringToByteArray(data)
    val res = context.execute(
      if (readOnly) {
        log.debug(s"static call to smart contract, address=$contractAddress, data=$data")
        invocation.staticCall(
          contractAddress,
          dataBytes,
          invocation.gasPool.getGas // we use all the amount we currently have
        )
      } else {
        log.debug(s"call to smart contract, address=$contractAddress, data=$data")
        invocation.call(
          contractAddress,
          value,
          dataBytes,
          invocation.gasPool.getGas // we use all the amount we currently have
        )
      }
    )

    val proxyInvocationEvent = ProxyInvocation(invocation.caller, contractAddress, dataBytes)
    val evmLog = getEthereumConsensusDataLog(proxyInvocationEvent)
    view.addLog(evmLog)

    // result in case of success execution might be useful for RPC commands
    log.debug(s"Exiting with res: ${BytesUtils.toHexString(res)}")

    res
  }


  @throws(classOf[ExecutionFailedException])
  override def process(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    log.debug(s"processing invocation: $invocation")

    val gasView = view.getGasTrackedView(invocation.gasPool)
    getFunctionSignature(invocation.input) match {
      case InvokeSmartContractCallCmd => doInvokeSmartContractCallCmd(invocation, gasView, context)
      case InvokeSmartContractStaticCallCmd => doInvokeSmartContractStaticCallCmd(invocation, gasView, context)
      case opCodeHex => throw new ExecutionRevertedException(s"op code not supported: $opCodeHex")
    }
  }
}

object ProxyMsgProcessor {

  val InvokeSmartContractCallCmd: String = getABIMethodId("invokeCall(address,bytes)")
  val InvokeSmartContractStaticCallCmd: String = getABIMethodId("invokeStaticCall(address,bytes)")

  // ensure we have strings consistent with size of opcode
  require(
    InvokeSmartContractCallCmd.length == 2 * METHOD_ID_LENGTH &&
      InvokeSmartContractStaticCallCmd.length == 2 * METHOD_ID_LENGTH
  )

}

