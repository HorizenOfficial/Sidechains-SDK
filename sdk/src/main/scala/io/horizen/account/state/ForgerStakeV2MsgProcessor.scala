package io.horizen.account.state

import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.fork.Version1_4_0Fork
import io.horizen.account.network.{ForgerInfo, PagedForgersOutput}
import io.horizen.account.state.nativescdata.forgerstakev2.StakeStorage.{addForger, getForger, updateForger}
import io.horizen.account.state.nativescdata.forgerstakev2._
import io.horizen.account.state.nativescdata.forgerstakev2.events._
import io.horizen.account.storage.MsgProcessorMetadataStorageReader
import io.horizen.account.utils.WellKnownAddresses.{FORGER_STAKE_SMART_CONTRACT_ADDRESS, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS}
import io.horizen.account.utils.ZenWeiConverter.{convertZenniesToWei, isValidZenAmount}
import io.horizen.consensus.{ForgingStakeInfo, minForgerStake}
import io.horizen.evm.Address
import io.horizen.proof.{Signature25519, VrfProof}
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.utils.BytesUtils
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric.cleanHexPrefix
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

trait ForgerStakesV2Provider {
  private[horizen] def getPagedForgersStakesByForger(view: BaseAccountStateView, forger: ForgerPublicKeys, startPos: Int, pageSize: Int): PagedStakesByForgerResponse
  private[horizen] def getPagedForgersStakesByDelegator(view: BaseAccountStateView, delegator: Address, startPos: Int, pageSize: Int): PagedStakesByDelegatorResponse
  private[horizen] def getPagedListOfForgersStakes(view: BaseAccountStateView, startPos: Int, pageSize: Int): PagedForgersListResponse
  private[horizen] def getListOfForgersStakes(view: BaseAccountStateView): Seq[ForgerStakeData]
  private[horizen] def getForgingStakes(view: BaseAccountStateView): Seq[ForgingStakeInfo]
  private[horizen] def getForgerInfo(view: BaseAccountStateView, forger: ForgerPublicKeys): Option[ForgerInfo]
  private[horizen] def isActive(view: BaseAccountStateView): Boolean
}


object ForgerStakeV2MsgProcessor extends NativeSmartContractWithFork  with ForgerStakesV2Provider {

  val MAX_REWARD_SHARE = 1000
  val MIN_REGISTER_FORGER_STAKED_AMOUNT_IN_WEI: BigInteger = convertZenniesToWei(minForgerStake) // 10 Zen
  val NUM_OF_EPOCHS_AFTER_FORK_ACTIVATION_FOR_UPDATE_FORGER: Int = 2

  override val contractAddress: Address = FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ForgerStakeV2SmartContractCode")

  override def isForkActive(consensusEpochNumber: Int): Boolean = {
    Version1_4_0Fork.get(consensusEpochNumber).active
  }

  override def process(invocation: Invocation, view: BaseAccountStateView, metadata: MsgProcessorMetadataStorageReader, context: ExecutionContext): Array[Byte] = {
    if (!isForkActive(context.blockContext.consensusEpochNumber))
      throw new ExecutionRevertedException(s"fork not active")
    val gasView = view.getGasTrackedView(invocation.gasPool)
    getFunctionSignature(invocation.input) match {
      case RegisterForgerCmd =>
        doRegisterForger(invocation, gasView, context)
      case UpdateForgerCmd =>
        doUpdateForger(invocation, gasView)
      case DelegateCmd =>
        doDelegateCmd(invocation, gasView, context)
      case WithdrawCmd =>
        doWithdrawCmd(invocation, gasView, context)
      case StakeTotalCmd =>
        doStakeTotalCmd(invocation, gasView, context.blockContext.consensusEpochNumber)
      case StakeStartCmd =>
        doStakeStartCmd(invocation, gasView)
      case RewardsReceivedCmd =>
        doRewardsReceivedCmd(invocation, gasView, metadata, context.blockContext.consensusEpochNumber)
      case GetPagedForgersStakesByForgerCmd =>
        doPagedForgersStakesByForgerCmd(invocation, gasView)
      case GetPagedForgersStakesByDelegatorCmd =>
        doPagedForgersStakesByDelegatorCmd(invocation, gasView)
      case GetCurrentConsensusEpochCmd =>
        doGetCurrentConsensusEpochCmd(invocation, gasView, context)
      case ActivateCmd =>
        doActivateCmd(invocation, view, context) // That shouldn't consume gas, so it doesn't use gasView
      case GetPagedForgersCmd =>
        doGetPagedForgersCmd(invocation, gasView)
      case GetForgerCmd =>
        doGetForgerCmd(invocation, gasView)
      case opCodeHex => throw new ExecutionRevertedException(s"op code not supported: $opCodeHex")
    }
  }


  def verifySignatures(msgToSign: Array[Byte], blockSignPubKey: PublicKey25519Proposition, vrfPubKey: VrfPublicKey, sign25519: Signature25519, signVrf: VrfProof): Unit = {
    if (!sign25519.isValid(blockSignPubKey, msgToSign)) {
      val errMsg = s"Invalid signature, could not validate against blockSignerProposition=$blockSignPubKey (sign=$sign25519)"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    if (!signVrf.isValid(vrfPubKey, msgToSign)) {
      val errMsg = s"Invalid signature, could not validate against vrfKey=$vrfPubKey (sign=$signVrf)"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }
  }

  def doRegisterForger(invocation: Invocation, gasView: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {

    checkForgerStakesV2IsActive(gasView)

    val stakedAmount = invocation.value

    // check that msg.value is a legal wei amount convertible to satoshis without any remainder and that
    // it is over the minimum threshold
    if (!isValidZenAmount(stakedAmount)) {
      val errMsg = s"Value is not a legal wei amount: ${stakedAmount.toString()}, maximum 10 decimals accepted"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }
    if (stakedAmount.compareTo(MIN_REGISTER_FORGER_STAKED_AMOUNT_IN_WEI) < 0) {
      val errMsg = s"Value ${stakedAmount.toString()} is below the minimum stake amount threshold: $MIN_REGISTER_FORGER_STAKED_AMOUNT_IN_WEI "
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = RegisterOrUpdateForgerCmdInputDecoder.decode(inputParams)

    val blockSignPubKey = cmdInput.forgerPublicKeys.blockSignPublicKey
    val vrfPubKey = cmdInput.forgerPublicKeys.vrfPublicKey
    val rewardShare = cmdInput.rewardShare
    val smartContractAddr = cmdInput.rewardAddress
    val sign25519 = cmdInput.signature25519
    val signVrf = cmdInput.signatureVrf


    if (gasView.getBalance(invocation.caller).subtract(stakedAmount).signum() < 0) {
      val errMsg = s"Not enough balance: ${cmdInput.forgerPublicKeys.toString}"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // check that rewardShare is in legal range
    if (rewardShare < 0 || rewardShare > MAX_REWARD_SHARE) {
      val errMsg = s"Illegal reward share value: = $rewardShare"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    if (rewardShare == 0 && smartContractAddr != Address.ZERO) {
      val errMsg = s"Reward share cannot be 0 if reward address is defined - Reward share = $rewardShare, reward address = $smartContractAddr"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }
    else if (rewardShare != 0 && smartContractAddr == Address.ZERO) {
      val errMsg = s"Reward share cannot be different from 0 if reward address is not defined - Reward share = $rewardShare, reward address = $smartContractAddr"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // we take for granted that forger list is open TODO comment also in fork list

    // check we do not have this forger yet. This is an early check, addForger will do it as well
    if (getForger(gasView, blockSignPubKey, vrfPubKey).isDefined) {
      val errMsg = s"Can not register an already existing forger: ${ForgerPublicKeys(blockSignPubKey, vrfPubKey).toString}"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    val messageToSign = getHashedMessageToSign(
      BytesUtils.toHexString(blockSignPubKey.pubKeyBytes()),
      BytesUtils.toHexString(vrfPubKey.pubKeyBytes()),
      rewardShare,
      BytesUtils.toHexString(smartContractAddr.toBytes))

    // verify the signatures (throws exceptions)
    verifySignatures(messageToSign, blockSignPubKey, vrfPubKey, sign25519, signVrf)

    // add new forger to the db
    val delegatorAddress = invocation.caller
    addForger(gasView, blockSignPubKey, vrfPubKey, rewardShare, smartContractAddr,
      context.blockContext.consensusEpochNumber, delegatorAddress, stakedAmount)

    gasView.subBalance(invocation.caller, stakedAmount)
    // increase the balance of the "forger stake smart contract” account
    gasView.addBalance(contractAddress, stakedAmount)

    val registerForgerEvent = RegisterForger(invocation.caller, blockSignPubKey, vrfPubKey, stakedAmount, rewardShare, smartContractAddr)
    val evmLog = getEthereumConsensusDataLog(registerForgerEvent)
    gasView.addLog(evmLog)

    log.debug(s"register forger exiting - ${cmdInput.toString}")
    Array.emptyByteArray
  }

  def doUpdateForger(invocation: Invocation, gasView: BaseAccountStateView): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(gasView)

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = RegisterOrUpdateForgerCmdInputDecoder.decode(inputParams)
    val blockSignPubKey = cmdInput.forgerPublicKeys.blockSignPublicKey
    val vrfPubKey = cmdInput.forgerPublicKeys.vrfPublicKey
    val rewardShare = cmdInput.rewardShare
    val rewardAddress = cmdInput.rewardAddress
    val sign25519 = cmdInput.signature25519
    val signVrf = cmdInput.signatureVrf

    // check that rewardShare is in legal range (0, MAX]
    if (rewardShare <= 0 || rewardShare > MAX_REWARD_SHARE) {
      val errMsg = s"Illegal reward share value: = $rewardShare"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    if (rewardAddress == Address.ZERO) {
      val errMsg = s"Reward address cannot be the ZERO address"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // check we do have this forger and get it
    val forger = getForger(gasView, blockSignPubKey, vrfPubKey) match {
      case Some(obj) => obj
      case None =>
        val errMsg = s"Forger does not exist: ${ForgerPublicKeys(blockSignPubKey, vrfPubKey).toString}"
        log.debug(errMsg)
        throw new ExecutionRevertedException(errMsg)
    }

    if (forger.rewardShare != 0 || forger.rewardAddress.address() != Address.ZERO) {
      val errMsg = s"Reward share or reward address are not null - Reward share = ${forger.rewardShare}, reward address = ${forger.rewardAddress}"
      log.debug(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    val messageToSign = getHashedMessageToSign(
      BytesUtils.toHexString(blockSignPubKey.pubKeyBytes()),
      BytesUtils.toHexString(vrfPubKey.pubKeyBytes()),
      rewardShare,
      BytesUtils.toHexString(rewardAddress.toBytes))

    // verify the signatures (throws exceptions)
    verifySignatures(messageToSign, blockSignPubKey, vrfPubKey, sign25519, signVrf)

    // update forger in the db
    updateForger(gasView, blockSignPubKey, vrfPubKey, rewardShare, rewardAddress)

    val updateForgerEvent = UpdateForger(invocation.caller, blockSignPubKey, vrfPubKey, rewardShare, rewardAddress)
    val evmLog = getEthereumConsensusDataLog(updateForgerEvent)
    gasView.addLog(evmLog)

    log.debug(s"update forger exiting - ${cmdInput.toString}")
    Array.emptyByteArray
  }

  def doDelegateCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {

    checkForgerStakesV2IsActive(view)
    val inputParams = getArgumentsFromData(invocation.input)
    val SelectByForgerCmdInput(forgerPublicKeys) = SelectByForgerCmdInputDecoder.decode(inputParams)

    log.debug(s"delegate called - $forgerPublicKeys")
    val stakedAmount = invocation.value

    if (stakedAmount.signum() <= 0) {
      val msg = "Value must not be zero"
      log.debug(msg)
      throw new ExecutionRevertedException(msg)
    }

    if (!isValidZenAmount(stakedAmount)) {
      val msg = s"Value is not a legal wei amount: $stakedAmount"
      log.debug(msg)
      throw new ExecutionRevertedException(msg)
    }

    if (view.getBalance(invocation.caller).compareTo(stakedAmount) < 0){
      throw new ExecutionRevertedException(s"Insufficient funds. Required: $stakedAmount, available: ${view.getBalance(invocation.caller)}")
    }

    val epochNumber = context.blockContext.consensusEpochNumber

    StakeStorage.addStake(view, forgerPublicKeys.blockSignPublicKey, forgerPublicKeys.vrfPublicKey,
      epochNumber, invocation.caller, stakedAmount)

    val delegateStakeEvt = DelegateForgerStake(invocation.caller, forgerPublicKeys.blockSignPublicKey, forgerPublicKeys.vrfPublicKey, stakedAmount)
    val evmLog = getEthereumConsensusDataLog(delegateStakeEvt)
    view.addLog(evmLog)

    view.subBalance(invocation.caller, stakedAmount)
    // increase the balance of the "forger stake smart contract” account
    view.addBalance(contractAddress, stakedAmount)

    Array.emptyByteArray
  }

  def doWithdrawCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val WithdrawCmdInput(forgerPublicKeys, stakedAmount) = WithdrawCmdInputDecoder.decode(inputParams)

    log.debug(s"withdraw called - $forgerPublicKeys $stakedAmount")

    if (stakedAmount.signum() != 1) {
      val msg = s"Withdrawal amount must be greater than zero: $stakedAmount"
      log.debug(msg)
      throw new ExecutionRevertedException(msg)
    }

    if (!isValidZenAmount(stakedAmount)) {
      val msg = s"Value is not a legal wei amount: $stakedAmount"
      log.debug(msg)
      throw new ExecutionRevertedException(msg)
    }

    val epochNumber = context.blockContext.consensusEpochNumber

    StakeStorage.removeStake(view, forgerPublicKeys.blockSignPublicKey, forgerPublicKeys.vrfPublicKey,
      epochNumber, invocation.caller, stakedAmount)

    val withdrawStakeEvt = WithdrawForgerStake(invocation.caller, forgerPublicKeys.blockSignPublicKey, forgerPublicKeys.vrfPublicKey, stakedAmount)
    val evmLog = getEthereumConsensusDataLog(withdrawStakeEvt)
    view.addLog(evmLog)

    view.subBalance(contractAddress, stakedAmount)
    view.addBalance(invocation.caller, stakedAmount)

    Array.emptyByteArray
  }

  def checkForgerStakesV2IsActive(view: BaseAccountStateView): Unit = {
    if (!StakeStorage.isActive(view)) {
      val msg = "Forger stake V2 has not been activated yet"
      log.debug(msg)
      throw new ExecutionRevertedException("Forger stake V2 has not been activated yet")
    }
  }

  def doStakeTotalCmd(invocation: Invocation, view: BaseAccountStateView, currentEpoch: Int): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = StakeTotalCmdInputDecoder.decode(inputParams)

    if (cmdInput.consensusEpochStart.isDefined && (cmdInput.consensusEpochStart.get > currentEpoch)) {
      val msgStr = s"Illegal argument - consensus epoch start ${cmdInput.consensusEpochStart.get} can not be greater than currentEpoch $currentEpoch"
      throw new ExecutionRevertedException(msgStr)
    }

    val forgerKeys = cmdInput.forgerPublicKeys
    val delegator = cmdInput.delegator
    val consensusEpochStart = if (cmdInput.consensusEpochStart.isEmpty) currentEpoch else cmdInput.consensusEpochStart.get
    val maxNumOfEpoch = cmdInput.maxNumOfEpoch
    log.info(s"stakeTotal called - $forgerKeys $delegator epochStart: $consensusEpochStart - maxNumOfEpoch: $maxNumOfEpoch")

    if (forgerKeys.isEmpty) {
      if (delegator.isDefined) {
        val msgStr = s"Illegal argument - delegator is defined while forger keys are not"
        throw new ExecutionRevertedException(msgStr)
      }
    } else {
      // check we do have this forger
      if (getForger(view, forgerKeys.get.blockSignPublicKey, forgerKeys.get.vrfPublicKey).isEmpty) {
          val errMsg = s"Forger does not exist: ${forgerKeys.toString}"
          log.debug(errMsg)
          throw new ExecutionRevertedException(errMsg)
      }
    }

    val consensusEpochEnd =
      if (maxNumOfEpoch.isEmpty) consensusEpochStart
      else if (consensusEpochStart + maxNumOfEpoch.get > currentEpoch) currentEpoch
      else consensusEpochStart + maxNumOfEpoch.get - 1

    val response: StakeTotalCmdOutput = StakeStorage.getStakeTotal(view, forgerKeys, delegator, consensusEpochStart, consensusEpochEnd)

    response.encode()
  }

  def doRewardsReceivedCmd(invocation: Invocation, view: BaseAccountStateView, metadata: MsgProcessorMetadataStorageReader, currentEpoch: Int): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = RewardsReceivedCmdInputDecoder.decode(inputParams)

    val forgerKeys: ForgerPublicKeys = cmdInput.forgerPublicKeys
    val consensusEpochStart: Int = cmdInput.consensusEpochStart

    if (consensusEpochStart >= currentEpoch) {
      val msgStr = s"Illegal argument - consensus epoch start $consensusEpochStart can not be greater than currentEpoch $currentEpoch"
      throw new ExecutionRevertedException(msgStr)
    }

    val maxNumOfEpoch: Int =
      if (consensusEpochStart + cmdInput.maxNumOfEpoch - 1 >= currentEpoch)
        currentEpoch - consensusEpochStart
      else
        cmdInput.maxNumOfEpoch

    log.info(s"rewardsReceived called - $forgerKeys epochStart: $consensusEpochStart")
    StakeStorage.getForger(view, forgerKeys.blockSignPublicKey, forgerKeys.vrfPublicKey)
      .getOrElse(throw new ExecutionRevertedException("Forger doesn't exist."))

    val rewards: Seq[BigInteger] = metadata.getForgerRewards(forgerKeys, consensusEpochStart, maxNumOfEpoch)
    val response = RewardsReceivedCmdOutput(rewards)

    response.encode()
  }

  def doStakeStartCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = StakeStartCmdInputDecoder.decode(inputParams)

    val forgerKeys = cmdInput.forgerPublicKeys
    val delegator = cmdInput.delegator
    log.info(s"stakeStart called - $forgerKeys $delegator")

    val response: StakeStartCmdOutput = StakeStorage.getStakeStart(view, forgerKeys, delegator)
    response.encode()
  }

  def doPagedForgersStakesByDelegatorCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = PagedForgersStakesByDelegatorCmdInputDecoder.decode(inputParams)
    log.debug(s"getPagedForgersStakesByDelegator called - ${cmdInput.delegator} startIndex: ${cmdInput.startIndex} - pageSize: ${cmdInput.pageSize}")

    val response = Try {
      // We must trap any exception arising and return the execution reverted exception
      StakeStorage.getPagedForgersStakesByDelegator(view, cmdInput.delegator, cmdInput.startIndex, cmdInput.pageSize)
    } match {
      case Success(response) => response
      case Failure(ex) =>
        throw new ExecutionRevertedException("Could not get paged result: " + ex.getMessage)
    }

    PagedForgersStakesByDelegatorOutput(response.nextStartPos, response.stakesData).encode()
  }

  def doPagedForgersStakesByForgerCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = PagedForgersStakesByForgerCmdInputDecoder.decode(inputParams)
    log.debug(s"getPagedForgersStakesByForger called - ${cmdInput.forgerPublicKeys} startIndex: ${cmdInput.startIndex} - pageSize: ${cmdInput.pageSize}")

    val response = Try {
      // We must trap any exception arising and return the execution reverted exception
      StakeStorage.getPagedForgersStakesByForger(view, cmdInput.forgerPublicKeys, cmdInput.startIndex, cmdInput.pageSize)
    } match {
      case Success(response) => response
      case Failure(ex) =>
        throw new ExecutionRevertedException("Could not get paged result: " + ex.getMessage)
    }
    PagedForgersStakesByForgerOutput(response.nextStartPos, response.stakesData).encode()
  }

  def doGetForgerCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val cmdInput = SelectByForgerCmdInputDecoder.decode(inputParams)

    val forgerOpt = StakeStorage.getForger(view, cmdInput.forgerPublicKeys.blockSignPublicKey, cmdInput.forgerPublicKeys.vrfPublicKey)
    if (forgerOpt.isEmpty)
      throw new ExecutionRevertedException("Forger doesn't exist.")

    forgerOpt.get.encode()
  }

  def doGetPagedForgersCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)

    val inputParams = getArgumentsFromData(invocation.input)
    val PagedForgersCmdInput(startPos, pageSize) = PagedForgersCmdInputDecoder.decode(inputParams)

    val res = StakeStorage.getPagedListOfForgers(view, startPos, pageSize)
    PagedForgersOutput(res.nextStartPos, res.forgers).encode()
  }

  def doGetCurrentConsensusEpochCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    requireIsNotPayable(invocation)
    checkForgerStakesV2IsActive(view)
    checkInputDoesntContainParams(invocation)

    ConsensusEpochCmdOutput(context.blockContext.consensusEpochNumber).encode()
  }

  def doActivateCmd(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {

    //Check is well formed
    requireIsNotPayable(invocation)
    checkInputDoesntContainParams(invocation)

    //Check it cannot be called twice
    if (StakeStorage.isActive(view)) {
      val msgStr = s"Forger stake V2 already activated"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }

    val intrinsicGas = invocation.gasPool.getUsedGas

    //Call "disableAndMigrate" on old forger stake msg processor, so it won't be used anymore.
    //It returns all existing stakes that will be recreated in the ForgerStakes V2
    val result = context.execute(invocation.call(FORGER_STAKE_SMART_CONTRACT_ADDRESS, BigInteger.ZERO,
      BytesUtils.fromHexString(ForgerStakeMsgProcessor.DisableAndMigrateCmd), invocation.gasPool.getGas))
    val listOfExistingStakes = AccountForgingStakeInfoListDecoder.decode(result).listOfStakes
    val stakesByForger = listOfExistingStakes.groupBy(_.forgerStakeData.forgerPublicKeys)

    val epochNumber = context.blockContext.consensusEpochNumber

    var totalMigratedStakeAmount = BigInteger.ZERO
    stakesByForger.foreach { case (forgerKeys, stakesByForger) =>
      // Sum the stakes by delegator
      val stakesByDelegator = stakesByForger.groupBy(_.forgerStakeData.ownerPublicKey)
      val listOfTotalStakesByDelegator = stakesByDelegator.mapValues(_.foldLeft(BigInteger.ZERO) {
        (sum, stake) => sum.add(stake.forgerStakeData.stakedAmount)
      })
      //Take first delegator for registering the forger
      val (firstDelegator, firstDelegatorStakeAmount) = listOfTotalStakesByDelegator.head
      //in this case we don't have to check the 10 ZEN minimum threshold for adding a new forger
      StakeStorage.addForger(view, forgerKeys.blockSignPublicKey,
        forgerKeys.vrfPublicKey, 0, Address.ZERO, epochNumber, firstDelegator.address(), firstDelegatorStakeAmount)
      totalMigratedStakeAmount = totalMigratedStakeAmount.add(firstDelegatorStakeAmount)
      listOfTotalStakesByDelegator.tail.foreach { case (delegator, delegatorStakeAmount) =>
        StakeStorage.addStake(view, forgerKeys.blockSignPublicKey, forgerKeys.vrfPublicKey,
          epochNumber, delegator.address(), delegatorStakeAmount)
        totalMigratedStakeAmount = totalMigratedStakeAmount.add(delegatorStakeAmount)
      }
    }

    //Update the balance of both forger stake msg processors
    view.subBalance(FORGER_STAKE_SMART_CONTRACT_ADDRESS, totalMigratedStakeAmount)
    view.addBalance(FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS, totalMigratedStakeAmount)

    // Refund the used gas, because activate should be free, except for the intrinsic gas
    invocation.gasPool.addGas(invocation.gasPool.getUsedGas.subtract(intrinsicGas))

    StakeStorage.setActive(view)

    val activateEvent = ActivateStakeV2()
    val evmLog = getEthereumConsensusDataLog(activateEvent)
    view.addLog(evmLog)

    log.info(s"Forger stakes V2 activated successfully - ${listOfExistingStakes.size} items migrated, " +
      s"total stake amount $totalMigratedStakeAmount")
    Array.emptyByteArray
  }

  val RegisterForgerCmd: String = getABIMethodId("registerForger(bytes32,bytes32,bytes1,uint32,address,bytes32,bytes32,bytes32,bytes32,bytes32,bytes1)")
  val UpdateForgerCmd: String = getABIMethodId("updateForger(bytes32,bytes32,bytes1,uint32,address,bytes32,bytes32,bytes32,bytes32,bytes32,bytes1)")
  val DelegateCmd: String = getABIMethodId("delegate(bytes32,bytes32,bytes1)")
  val WithdrawCmd: String = getABIMethodId("withdraw(bytes32,bytes32,bytes1,uint256)")
  val StakeTotalCmd: String = getABIMethodId("stakeTotal(bytes32,bytes32,bytes1,address,uint32,uint32)")
  val StakeStartCmd: String = getABIMethodId("stakeStart(bytes32,bytes32,bytes1,address)")
  val RewardsReceivedCmd: String = getABIMethodId("rewardsReceived(bytes32,bytes32,bytes1,uint32,uint32)")
  val GetPagedForgersStakesByForgerCmd: String = getABIMethodId("getPagedForgersStakesByForger(bytes32,bytes32,bytes1,int32,int32)")
  val GetPagedForgersStakesByDelegatorCmd: String = getABIMethodId("getPagedForgersStakesByDelegator(address,int32,int32)")
  val ActivateCmd: String = getABIMethodId("activate()")
  val GetForgerCmd: String = getABIMethodId("getForger(bytes32,bytes32,bytes1)")
  val GetPagedForgersCmd: String = getABIMethodId("getPagedForgers(int32,int32)")
  val GetCurrentConsensusEpochCmd: String = getABIMethodId("getCurrentConsensusEpoch()")

  // ensure we have strings consistent with size of opcode
  require(
    RegisterForgerCmd.length == 2 * METHOD_ID_LENGTH &&
      UpdateForgerCmd.length == 2 * METHOD_ID_LENGTH &&
      DelegateCmd.length == 2 * METHOD_ID_LENGTH &&
      WithdrawCmd.length == 2 * METHOD_ID_LENGTH &&
      StakeTotalCmd.length == 2 * METHOD_ID_LENGTH &&
      StakeStartCmd.length == 2 * METHOD_ID_LENGTH &&
      RewardsReceivedCmd.length == 2 * METHOD_ID_LENGTH &&
      ActivateCmd.length == 2 * METHOD_ID_LENGTH &&
      GetPagedForgersStakesByForgerCmd.length == 2 * METHOD_ID_LENGTH &&
      GetPagedForgersStakesByDelegatorCmd.length == 2 * METHOD_ID_LENGTH &&
      GetForgerCmd.length == 2 * METHOD_ID_LENGTH &&
      GetPagedForgersCmd.length == 2 * METHOD_ID_LENGTH &&
      GetCurrentConsensusEpochCmd.length == 2 * METHOD_ID_LENGTH
  )

  def getHashedMessageToSign(blockSignPubKeyStr: String, vrfPublicKeyStr: String, rewardShare: Int, rewardAddress: String): Array[Byte] = {
    // we use lower case representation for hex strings and checksum format for address
    val messageToSignString = cleanHexPrefix(blockSignPubKeyStr).toLowerCase + cleanHexPrefix(vrfPublicKeyStr).toLowerCase + rewardShare.toString + Keys.toChecksumAddress(rewardAddress)
    // we take only first 31 bytes since vrf signature is involved, and we must be on the safe side using less than a field element's modulus bits size (253 bites)
    Keccak256.hash(messageToSignString.getBytes(StandardCharsets.UTF_8)).take(31)
  }

  override private[horizen] def getPagedListOfForgersStakes(view: BaseAccountStateView, startPos: Int, pageSize: Int): PagedForgersListResponse = {
    StakeStorage.getPagedListOfForgers(view, startPos, pageSize)
  }

  override private[horizen] def getListOfForgersStakes(view: BaseAccountStateView): Seq[ForgerStakeData] = {
    StakeStorage.getAllForgerStakes(view)
  }

  override private[horizen] def getPagedForgersStakesByForger(view: BaseAccountStateView, forger: ForgerPublicKeys, startPos: Int, pageSize: Int): PagedStakesByForgerResponse = {
    checkForgerStakesV2IsActive(view)
    StakeStorage.getPagedForgersStakesByForger(view, forger, startPos, pageSize)
  }

  override private[horizen] def getPagedForgersStakesByDelegator(view: BaseAccountStateView, delegator: Address, startPos: Int, pageSize: Int): PagedStakesByDelegatorResponse = {
    checkForgerStakesV2IsActive(view)
    StakeStorage.getPagedForgersStakesByDelegator(view, delegator, startPos, pageSize)
  }

  override private[horizen] def getForgingStakes(view: BaseAccountStateView): Seq[ForgingStakeInfo] = {
    StakeStorage.getForgingStakes(view)
  }

  override private[horizen] def getForgerInfo(view: BaseAccountStateView, forger: ForgerPublicKeys): Option[ForgerInfo] = {
    StakeStorage.getForger(view, forger.blockSignPublicKey, forger.vrfPublicKey)
  }

  override private[horizen] def isActive(view: BaseAccountStateView): Boolean = StakeStorage.isActive(view)



}
