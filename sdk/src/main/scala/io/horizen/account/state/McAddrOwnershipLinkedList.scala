package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS

import scala.util.{Failure, Success}

object McAddrOwnershipLinkedList extends NativeSmartContractLinkedList {

  override val listTipKey: Array[Byte] = McAddrOwnershipMsgProcessor.LinkedListTipKey
  override val listNullValue: Array[Byte] = McAddrOwnershipMsgProcessor.LinkedListNullValue

  def findOwnershipData(view: BaseAccountStateView, ownershipId: Array[Byte]): Option[McAddrOwnershipData] = {
    val data = view.getAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, ownershipId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      McAddrOwnershipDataSerializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger data.", exception)
      }
    }
  }

  def getOwnershipListItem(view: BaseAccountStateView, tip: Array[Byte]): (McAddrOwnershipData, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(tip)) {
      val node = findLinkedListNode(view, tip, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS).get
      val ownershipData = findOwnershipData(view, node.dataKey).get
      val listItem = McAddrOwnershipData(ownershipData.scAddress, ownershipData.mcTransparentAddress)
      val prevNodeKey = node.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

}
