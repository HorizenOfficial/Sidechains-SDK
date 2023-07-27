package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.state.ProxyMsgProcessor._
import io.horizen.account.state.events.ProxyInvocation
import io.horizen.account.utils.WellKnownAddresses.PROXY_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.params.NetworkParams
import io.horizen.utils.BytesUtils
import org.web3j.utils.Numeric
import sparkz.crypto.hash.Keccak256

trait ProxyProvider {

}

case class ProxyMsgProcessor(params: NetworkParams) extends NativeSmartContractMsgProcessor with ProxyProvider {

  override val contractAddress: Address = PROXY_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ProxySmartContractCode")

  val networkParams: NetworkParams = params

  def getStakeId(msg: Message): Array[Byte] = {
    Keccak256.hash(Bytes.concat(
      msg.getFrom.toBytes, msg.getNonce.toByteArray, msg.getValue.toByteArray, msg.getData))
  }

  // TODO this must be updated when merging with 0.8.0, we should support consensusEpochNumber for hard fork activation
  override def init(view: BaseAccountStateView): Unit = {
    super.init(view)
  }

  def doInvokeSmartContractCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {

    val msg = context.msg
    log.info(s"Entering with msg: $msg")
    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
    if (msg.getNonce == null) {
      throw new ExecutionRevertedException("Call must include a nonce")
    }

    val value = invocation.value

    // check that msg.value is greater or equal than zero
    if (value.signum() < 0) {
      throw new ExecutionRevertedException("Value must not be zero")
    }

    // check that sender account exists (unless we are staking in the sc creation phase)
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

    log.info(s"calling smart contract: address: $contractAddress, data=$data")
    val dataBytes = Numeric.hexStringToByteArray(data)
    val res = context.execute(
      invocation.call(
        contractAddress,
        value,
        dataBytes,
        invocation.gasPool.getGas // we use all the amount we currently have
      )
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
    val gasView = view.getGasTrackedView(invocation.gasPool)
    getFunctionSignature(invocation.input) match {
      case InvokeSmartContractCallCmd => doInvokeSmartContractCmd(invocation, gasView, context)
      case opCodeHex => throw new ExecutionRevertedException(s"op code not supported: $opCodeHex")
    }
  }

}

object ProxyMsgProcessor {

  val InvokeSmartContractCallCmd: String = getABIMethodId("invokeCall(address,string)")
  val InvokeSmartContractStaticCallCmd: String = getABIMethodId("invokeStaticCall(address,string)")

  // ensure we have strings consistent with size of opcode
  require(
    InvokeSmartContractCallCmd.length == 2 * METHOD_ID_LENGTH &&
      InvokeSmartContractStaticCallCmd.length == 2 * METHOD_ID_LENGTH
  )

}

