package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS

import scala.util.{Failure, Success}

object McAddrOwnershipLinkedList extends NativeSmartContractLinkedList {

  override val listTipKey: Array[Byte] = McAddrOwnershipMsgProcessor.OwnershipsLinkedListTipKey
  override val listTipNullValue: Array[Byte] = McAddrOwnershipMsgProcessor.OwnershipLinkedListNullValue

  def getOwnershipData(view: BaseAccountStateView, ownershipId: Array[Byte]): Option[McAddrOwnershipData] = {
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

  def getOwnershipListItem(view: BaseAccountStateView, nodeRef: Array[Byte]): (McAddrOwnershipData, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(nodeRef)) {

      val node = getLinkedListNode(view, nodeRef, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
        .orElse(throw new ExecutionRevertedException("Could not find a valid node"))

      val ownershipData = getOwnershipData(view, node.get.dataKey)
        .orElse(throw new ExecutionRevertedException("Could not find valid data"))

      val listItem = McAddrOwnershipData(ownershipData.get.scAddress, ownershipData.get.mcTransparentAddress)
      val prevNodeKey = node.get.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

}
