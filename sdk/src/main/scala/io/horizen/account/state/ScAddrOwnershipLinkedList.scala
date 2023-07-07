package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS

import scala.util.{Failure, Success}

class ScAddrOwnershipLinkedList(listTipKeyIn: Array[Byte], listNullValueIn: Array[Byte])
  extends NativeSmartContractLinkedList {

  override val listTipKey: Array[Byte] = listTipKeyIn
  override val listNullValue: Array[Byte] = listNullValueIn

  def findScAddressData(view: BaseAccountStateView, scAddressId: Array[Byte]): Option[McAddrOwnershipData] = {
    val data = view.getAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, scAddressId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      // TODO we could use a different data serializer with just mc addr part
      McAddrOwnershipDataSerializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger data.", exception)
      }
    }
  }

  def getScAddressListItem(view: BaseAccountStateView, nodeRef: Array[Byte]): (McAddrOwnershipData, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(nodeRef)) {

      val node = getLinkedListNode(view, nodeRef, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
        .orElse(throw new ExecutionRevertedException("Could not find a valid node"))

      val ownershipData = findScAddressData(view, node.get.dataKey)
        .orElse(throw new ExecutionRevertedException("Could not find valid data"))

      // TODO we could use a different data serializer with just mc addr part
      val listItem = McAddrOwnershipData(ownershipData.get.scAddress, ownershipData.get.mcTransparentAddress)
      val prevNodeKey = node.get.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

}
