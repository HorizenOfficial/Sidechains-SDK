package io.horizen.account.state

import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.state.ProxyMsgProcessor._
import io.horizen.account.state.events.ProxyInvocation
import io.horizen.account.utils.WellKnownAddresses.PROXY_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.params.{MainNetParams, NetworkParams}
import io.horizen.utils.BytesUtils
import org.web3j.utils.Numeric
import sparkz.crypto.hash.Keccak256

trait ProxyProvider {

}

case class ProxyMsgProcessor(params: NetworkParams) extends NativeSmartContractMsgProcessor with ProxyProvider {

  override val contractAddress: Address = PROXY_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ProxySmartContractCode")

  // TODO this must be updated when merging with 0.8.0, we should support consensusEpochNumber for hard fork activation
  override def init(view: BaseAccountStateView): Unit = {
    super.init(view)
  }

  def doInvokeSmartContractStaticCallCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    doInvokeSmartContractCmd(invocation, view, context, readOnly = true)
  }

  def doInvokeSmartContractCallCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    doInvokeSmartContractCmd(invocation, view, context, readOnly = false)
  }

  private def doInvokeSmartContractCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext, readOnly : Boolean): Array[Byte] = {

    val msg = context.msg
    log.debug(s"Entering with invocation: $invocation")
    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
//    if (msg.getNonce == null) {
//      throw new ExecutionRevertedException("Call must include a nonce")
//    }

    val value = invocation.value

    // check that msg.value is greater or equal than zero
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
    val additionalDepth = 0
    val res = context.executeDepth(
      if (readOnly) {
        log.info(s"static call to smart contract, address=$contractAddress, data=$data")
        invocation.staticCall(
          contractAddress,
          dataBytes,
          invocation.gasPool.getGas // we use all the amount we currently have
        )
      } else {
        log.info(s"call to smart contract, address=$contractAddress, data=$data")
        invocation.call(
          contractAddress,
          value,
          dataBytes,
          invocation.gasPool.getGas // we use all the amount we currently have
        )
      },
      additionalDepth
    )

    val proxyInvocationEvent = ProxyInvocation(invocation.caller, contractAddress, dataBytes)
    val evmLog = getEthereumConsensusDataLog(proxyInvocationEvent)
    view.addLog(evmLog)

    // result in case of success execution might be useful for RPC commands
    log.info(s"Exiting with res: ${BytesUtils.toHexString(res)}")

    res
  }

  @throws(classOf[ExecutionFailedException])
  override def process(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    log.info(s"processing invocation: $invocation")

    if (params.isInstanceOf[MainNetParams]) {
      val errMsg = "Proxy Native Smart Contract is not supported in MainNet"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

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

