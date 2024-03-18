package io.horizen.account.state

import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.fork.Version1_4_0Fork
import io.horizen.account.state.ForgerStakeV2MsgProcessor.{DelegateCmd, GetPagedForgersStakesByDelegatorCmd, GetPagedForgersStakesByForgerCmd, StakeTotalCmd, WithdrawCmd}
import io.horizen.account.state.nativescdata.forgerstakev2.{DelegateCmdInputDecoder, PagedForgersStakesByDelegatorCmdInputDecoder, PagedForgersStakesByForgerCmdInputDecoder, StakeTotalCmdInputDecoder, WithdrawCmdInputDecoder}
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.params.NetworkParams
import sparkz.crypto.hash.Keccak256

case class ForgerStakeV2MsgProcessor(networkParams: NetworkParams) extends NativeSmartContractWithFork {
  override val contractAddress: Address = FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS
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
      case StakeTotalCmd if Version1_4_0Fork.get(context.blockContext.consensusEpochNumber).active =>
        doStakeTotalCmd(invocation, gasView, context.msg)
      case GetPagedForgersStakesByForgerCmd if Version1_4_0Fork.get(context.blockContext.consensusEpochNumber).active =>
        doPagedForgersStakesByForgerCmd(invocation, gasView, context.msg)
      case GetPagedForgersStakesByDelegatorCmd if Version1_4_0Fork.get(context.blockContext.consensusEpochNumber).active =>
        doPagedForgersStakesByDelegatorCmd(invocation, gasView, context.msg)
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

  def doStakeTotalCmd(invocation: Invocation, gasView: BaseAccountStateView, msg: Message): Array[Byte] = {
    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = StakeTotalCmdInputDecoder.decode(inputParams)
    log.info(s"stakeTotal called - ${cmdInput.forgerPublicKeys} ${cmdInput.delegator} epochStart: ${cmdInput.consensusEpochStart} - maxNumOfEpoch: ${cmdInput.maxNumOfEpoch}")

    //TODO: add logic and return data

    Array.emptyByteArray
  }

  def doPagedForgersStakesByDelegatorCmd(invocation: Invocation, gasView: BaseAccountStateView, msg: Message): Array[Byte] = {
    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = PagedForgersStakesByDelegatorCmdInputDecoder.decode(inputParams)
    log.info(s"getPagedForgersStakesByDelegator called - ${cmdInput.delegator} startIndex: ${cmdInput.startIndex} - pageSize: ${cmdInput.pageSize}")

    //TODO: add logic and return data

    Array.emptyByteArray
  }

  def doPagedForgersStakesByForgerCmd(invocation: Invocation, gasView: BaseAccountStateView, msg: Message): Array[Byte] = {
    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = PagedForgersStakesByForgerCmdInputDecoder.decode(inputParams)
    log.info(s"getPagedForgersStakesByForger called - ${cmdInput.forgerPublicKeys} startIndex: ${cmdInput.startIndex} - pageSize: ${cmdInput.pageSize}")

    //TODO: add logic and return data

    Array.emptyByteArray
  }

}

object ForgerStakeV2MsgProcessor {

  val DelegateCmd: String = getABIMethodId("delegate(bytes32,bytes32,bytes1)")
  val WithdrawCmd: String = getABIMethodId("withdraw(bytes32,bytes32,bytes1,uint256)")
  val StakeTotalCmd: String = getABIMethodId("stakeTotal(bytes32,bytes32,bytes1,address,uint32,uint32)")
  val GetPagedForgersStakesByForgerCmd: String = getABIMethodId("getPagedForgersStakesByForger(bytes32,bytes32,bytes1,int32,int32)");
  val GetPagedForgersStakesByDelegatorCmd: String = getABIMethodId("getPagedForgersStakesByDelegator(address,int32,int32)");

  // ensure we have strings consistent with size of opcode
  require(
    DelegateCmd.length == 2 * METHOD_ID_LENGTH &&
    WithdrawCmd.length == 2 * METHOD_ID_LENGTH &&
    StakeTotalCmd.length == 2 * METHOD_ID_LENGTH &&
    GetPagedForgersStakesByForgerCmd.length == 2 * METHOD_ID_LENGTH &&
    GetPagedForgersStakesByDelegatorCmd.length == 2 * METHOD_ID_LENGTH
  )
}
