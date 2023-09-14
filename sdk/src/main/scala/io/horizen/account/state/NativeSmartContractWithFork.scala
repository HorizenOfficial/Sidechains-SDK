package io.horizen.account.state

import io.horizen.utils.BytesUtils

abstract class NativeSmartContractWithFork extends NativeSmartContractMsgProcessor {


  override def init(view: BaseAccountStateView, consensusEpochNumber: Int): Unit = {
    if (isForkActive(consensusEpochNumber)) {
      if (initDone(view)) {
        log.error("Message processor already initialized")
        throw new MessageProcessorInitializationException("Message processor already initialized")
      }
      // We do not call the parent init() method because it would throw an exception if the account already exists.
      // In our case the initialization does not happen at genesis state, and someone might
      // (on purpose or not) already have sent funds to the account, maybe from a deployed solidity smart contract or
      // by means of an eoa transaction before fork activation
      if (!view.accountExists(contractAddress)) {
        log.debug(s"Creating Message Processor account $contractAddress")
      } else {
        // TODO maybe we can check the balance at this point and transfer the amount somewhere
        val msg = s"Account $contractAddress already exists!! Overwriting account with contract code ${BytesUtils.toHexString(contractCode)}..."
        log.warn(msg)
      }
      view.addAccount(contractAddress, contractCode)

      doSpecificInit(view, consensusEpochNumber)

    }
    else
      log.warn("Can not perform initialization, fork is not active")
  }

  override def canProcess(invocation: Invocation, view: BaseAccountStateView, consensusEpochNumber: Int): Boolean = {
    if (super.canProcess(invocation, view, consensusEpochNumber)) {
      if (isForkActive(consensusEpochNumber)) {
        // the gas cost of these calls is not taken into account in this case, we are not tracking gas consumption (and
        // there is not an account to charge anyway)
        if (!initDone(view))
          init(view, consensusEpochNumber)
        true
      } else {
        // we can not handle anything before fork activation, but just warn if someone is trying to use it
        log.warn(s"Can not process invocation, fork is not active: invocation = $invocation")
        false
      }
    } else
      false
  }


  protected def doSpecificInit(view: BaseAccountStateView, consensusEpochNumber: Int): Unit = ()

  def isForkActive(consensusEpochNumber: Int): Boolean = false

  def initDone(view: BaseAccountStateView): Boolean = {
    view.accountExists(contractAddress) && contractCodeHash.sameElements(view.getCodeHash(contractAddress))
  }


}