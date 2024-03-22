package io.horizen.account.state

import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.fork.Version1_4_0Fork
import io.horizen.account.state.ForgerStakeV2MsgProcessor._
import io.horizen.account.state.events.StakeUpgradeV2
import io.horizen.account.state.nativescdata.forgerstakev2._
import io.horizen.account.utils.WellKnownAddresses
import io.horizen.account.utils.WellKnownAddresses.{FORGER_STAKE_SMART_CONTRACT_ADDRESS, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS}
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger

object ForgerStakeV2MsgProcessor extends NativeSmartContractWithFork {
  override val contractAddress: Address = FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ForgerStakeV2SmartContractCode")

  override def isForkActive(consensusEpochNumber: Int): Boolean = {
    Version1_4_0Fork.get(consensusEpochNumber).active
  }
  
  override def process(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    if (!Version1_4_0Fork.get(context.blockContext.consensusEpochNumber).active)
      throw new ExecutionRevertedException(s"fork not active")
    val gasView = view.getGasTrackedView(invocation.gasPool)
    getFunctionSignature(invocation.input) match {
      case DelegateCmd =>
        doDelegateCmd(invocation, gasView, context.msg)
      case WithdrawCmd =>
        doWithdrawCmd(invocation, gasView, context.msg)
      case StakeTotalCmd  =>
        doStakeTotalCmd(invocation, gasView, context.msg)
      case GetPagedForgersStakesByForgerCmd  =>
        doPagedForgersStakesByForgerCmd(invocation, gasView, context.msg)
      case GetPagedForgersStakesByDelegatorCmd  =>
        doPagedForgersStakesByDelegatorCmd(invocation, gasView, context.msg)
      case UpgradeCmd  =>
        doUpgradeCmd(invocation, view, context) // That shouldn't consume gas, so it doesn't use gasView
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

  def doUpgradeCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    requireIsNotPayable(invocation)

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = UpgradeCmdInputDecoder.decode(inputParams)

    if (cmdInput.newVersion != ForgerStakeStorageVersion.VERSION_3.id)
      throw new ExecutionRevertedException( s"Storage version not supported: ${cmdInput.newVersion}")
    if (ForgerStakeStorageV3.getStorageVersionFromDb(view) == ForgerStakeStorageVersion.VERSION_3) {
      val msgStr = s"Forger stake storage already upgraded"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }

    val result = context.execute(invocation.call(FORGER_STAKE_SMART_CONTRACT_ADDRESS, BigInteger.ZERO,
      BytesUtils.fromHexString(ForgerStakeMsgProcessor.GetListOfForgersCmd), invocation.gasPool.getGas))
    val listOfExistingStakes = AccountForgingStakeInfoListDecoder.decode(result).listOfStakes
    ForgerStakeStorageV3.addForger(view, listOfExistingStakes.head.forgerStakeData.forgerPublicKeys.blockSignPublicKey,
      listOfExistingStakes.head.forgerStakeData.forgerPublicKeys.vrfPublicKey,
      0, Address.ZERO, 0,  listOfExistingStakes.head.forgerStakeData.ownerPublicKey.address(), listOfExistingStakes.head.forgerStakeData.stakedAmount)
//
//    val forgerStakeStorage = ForgerStakeStorage(ForgerStakeStorageVersion.VERSION_2)
//
//    var nodeReference = view.getAccountStorage(contractAddress, LinkedListTipKey)
//
//    var numOfMigratedElem = 0
//    while (!linkedListNodeRefIsNull(nodeReference)) {
//      val (item: AccountForgingStakeInfo, prevNodeReference: Array[Byte]) = getStakeListItem(view, nodeReference)
//
//      forgerStakeStorage.addForgerStake(view, item.stakeId,
//        item.forgerStakeData.forgerPublicKeys.blockSignPublicKey, item.forgerStakeData.forgerPublicKeys.vrfPublicKey,
//        item.forgerStakeData.ownerPublicKey.address(), item.forgerStakeData.stakedAmount)
//
//      removeNode(view, item.stakeId, contractAddress)
//      nodeReference = prevNodeReference
//      numOfMigratedElem = numOfMigratedElem + 1
//    }
//    view.removeAccountStorage(contractAddress, LinkedListTipKey)


    //Call "disable" on old forger stake msg processor, so it won't be used anymore
    context.execute(invocation.call(FORGER_STAKE_SMART_CONTRACT_ADDRESS, BigInteger.ZERO,
      BytesUtils.fromHexString(ForgerStakeMsgProcessor.DisableCmd), invocation.gasPool.getGas))

    val upgradeEvent = StakeUpgradeV2(ForgerStakeStorageVersion.VERSION_2.id, ForgerStakeStorageVersion.VERSION_3.id)
    val evmLog = getEthereumConsensusDataLog(upgradeEvent)
    view.addLog(evmLog)

//    log.info(s"Forger stakes storage upgraded successfully to version 3 - $numOfMigratedElem items migrated")

    ForgerStakeStorageV3.saveStorageVersion(view, ForgerStakeStorageVersion.VERSION_3)
  }


  val DelegateCmd: String = getABIMethodId("delegate(bytes32,bytes32,bytes1)")
  val WithdrawCmd: String = getABIMethodId("withdraw(bytes32,bytes32,bytes1,uint256)")
  val StakeTotalCmd: String = getABIMethodId("stakeTotal(bytes32,bytes32,bytes1,address,uint32,uint32)")
  val GetPagedForgersStakesByForgerCmd: String = getABIMethodId("getPagedForgersStakesByForger(bytes32,bytes32,bytes1,int32,int32)")
  val GetPagedForgersStakesByDelegatorCmd: String = getABIMethodId("getPagedForgersStakesByDelegator(address,int32,int32)")
  val UpgradeCmd: String = getABIMethodId("upgrade(int32)")

  // ensure we have strings consistent with size of opcode
  require(
    DelegateCmd.length == 2 * METHOD_ID_LENGTH &&
    WithdrawCmd.length == 2 * METHOD_ID_LENGTH &&
    StakeTotalCmd.length == 2 * METHOD_ID_LENGTH &&
    UpgradeCmd.length == 2 * METHOD_ID_LENGTH &&
    GetPagedForgersStakesByForgerCmd.length == 2 * METHOD_ID_LENGTH &&
    GetPagedForgersStakesByDelegatorCmd.length == 2 * METHOD_ID_LENGTH
  )
}
