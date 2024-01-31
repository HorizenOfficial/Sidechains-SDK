package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS

object ForgerStakeLinkedList  extends NativeSmartContractLinkedList {

  override val listTipKey: Array[Byte] = ForgerStakeStorageV1.LinkedListTipKey
  override val listTipNullValue: Array[Byte] = ForgerStakeStorageV1.LinkedListNullValue

  def getStakeListItem(view: BaseAccountStateView, tip: Array[Byte]): (AccountForgingStakeInfo, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(tip)) {
      val node = getLinkedListNode(view, tip, FORGER_STAKE_SMART_CONTRACT_ADDRESS).get
      val stakeData = ForgerStakeStorageV1.findStakeData(view, node.dataKey).get
      val listItem = AccountForgingStakeInfo(
        node.dataKey,
        ForgerStakeData(
          ForgerPublicKeys(
            stakeData.forgerPublicKeys.blockSignPublicKey, stakeData.forgerPublicKeys.vrfPublicKey),
          stakeData.ownerPublicKey, stakeData.stakedAmount)
      )
      val prevNodeKey = node.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

}
