package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS

import scala.util.{Failure, Success}

object ScAddressRefsLinkedList extends NativeSmartContractLinkedList {

  override val listTipKey: Array[Byte] = McAddrOwnershipMsgProcessor.ScAddressRefsLinkedListTipKey
  override val listTipNullValue: Array[Byte] = McAddrOwnershipMsgProcessor.ScAddressRefsLinkedListNullValue

  def getScAddressRefData(view: BaseAccountStateView, scRefId: Array[Byte]): Array[Byte] = {
    view.getAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, scRefId)
  }

  def getScAddresRefsListItem(view: BaseAccountStateView, nodeRef: Array[Byte]): (Array[Byte], Array[Byte]) = {
    if (!linkedListNodeRefIsNull(nodeRef)) {

      val node = getLinkedListNode(view, nodeRef, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
        .orElse(throw new ExecutionRevertedException("Could not find a valid node"))

      val listItem =  getScAddressRefData(view, node.get.dataKey)
      val prevNodeKey = node.get.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

}
