package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS

import scala.util.{Failure, Success}

object ScAddressRefsLinkedList extends NativeSmartContractLinkedList {

  override val listTipKey: Array[Byte] = McAddrOwnershipMsgProcessor.ScAddressRefsLinkedListTipKey
  override val listTipNullValue: Array[Byte] = McAddrOwnershipMsgProcessor.ScAddressRefsLinkedListNullValue

  def getScAddressRefData(view: BaseAccountStateView, scRefId: Array[Byte]): Option[OwnerScAddress] = {
    val data = view.getAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, scRefId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      OwnerScAddressSerializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger data.", exception)
      }
    }
  }

  def getScAddresRefsListItem(view: BaseAccountStateView, nodeRef: Array[Byte]): (OwnerScAddress, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(nodeRef)) {

      val node = getLinkedListNode(view, nodeRef, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
        .orElse(throw new ExecutionRevertedException("Could not find a valid node"))

      val listItem =  getScAddressRefData(view, node.get.dataKey)
        .orElse(throw new ExecutionRevertedException("Could not find valid data"))

      val prevNodeKey = node.get.previousNodeKey
      (listItem.get, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

}