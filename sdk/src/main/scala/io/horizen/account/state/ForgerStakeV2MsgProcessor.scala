package io.horizen.account.state

import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.fork.Version1_4_0Fork
import io.horizen.account.state.ForgerStakeV2MsgProcessor.{DelegateCmd, WithdrawCmd}
import io.horizen.account.state.nativescdata.forgerstakev2.{DelegateCmdInputDecoder, WithdrawCmdInputDecoder}
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKEV2_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.params.NetworkParams
import sparkz.crypto.hash.Keccak256

case class ForgerStakeV2MsgProcessor(networkParams: NetworkParams) extends NativeSmartContractWithFork {
  override val contractAddress: Address = FORGER_STAKEV2_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ForgerStakeV2SmartContractCode")

  override def isForkActive(consensusEpochNumber: Int): Boolean = {
    Version1_4_0Fork.get(consensusEpochNumber).active
  }
  
  override def process(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    val gasView = view.getGasTrackedView(invocation.gasPool)
    getFunctionSignature(invocation.input) match {
      case DelegateCmd if Version1_4_0Fork.get(context.blockContext.consensusEpochNumber).active =>
        doDelegateCmd(invocation, gasView, context.msg)
      case WithdrawCmd if Version1_4_0Fork.get(context.blockContext.consensusEpochNumber).active =>
        doWithdrawCmd(invocation, gasView, context.msg)
      case opCodeHex => throw new ExecutionRevertedException(s"op code not supported: $opCodeHex")
    }
  }

  def doDelegateCmd(invocation: Invocation, gasView: BaseAccountStateView, msg: Message): Array[Byte] = {
    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = DelegateCmdInputDecoder.decode(inputParams)
    log.info(s"delegate called - ${cmdInput.forgerPublicKeys}")
    //TODO: add logic
    Array.emptyByteArray
  }

  def doWithdrawCmd(invocation: Invocation, gasView: BaseAccountStateView, msg: Message): Array[Byte] = {
    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = WithdrawCmdInputDecoder.decode(inputParams)
    log.info(s"withdraw called - ${cmdInput.forgerPublicKeys} ${cmdInput.value}")

    //TODO: add logic
    Array.emptyByteArray
  }
}

object ForgerStakeV2MsgProcessor {

  val DelegateCmd: String = getABIMethodId("delegate(bytes32,bytes32,bytes1)")
  val WithdrawCmd: String = getABIMethodId("withdraw(bytes32,bytes32,bytes1,uint256)")

  // ensure we have strings consistent with size of opcode
  require(
    DelegateCmd.length == 2 * METHOD_ID_LENGTH &&
    WithdrawCmd.length == 2 * METHOD_ID_LENGTH
  )
}
