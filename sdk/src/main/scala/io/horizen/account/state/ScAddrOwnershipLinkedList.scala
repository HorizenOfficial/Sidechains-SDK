package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
import sparkz.crypto.hash.Blake2b256

import scala.util.{Failure, Success}

class ScAddrOwnershipLinkedList(scAddressStringNoPrefix : String)
  extends NativeSmartContractLinkedList {

  val listTipKey: Array[Byte] = Blake2b256.hash(scAddressStringNoPrefix+"Tip")
  val listTipNullValue: Array[Byte] = Blake2b256.hash(scAddressStringNoPrefix+"ListNull")

  val scAddressTipValue : Array[Byte] = Blake2b256.hash(scAddressStringNoPrefix)

  def getScAddrOwnershipData(view: BaseAccountStateView, scAddressId: Array[Byte]): Option[McAddrOwnershipData] = {
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

  def getScAddrOwnershipListItem(view: BaseAccountStateView, nodeRef: Array[Byte]): (McAddrOwnershipData, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(nodeRef)) {

      val node = getLinkedListNode(view, nodeRef, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
        .orElse(throw new ExecutionRevertedException("Could not find a valid node"))

      val ownershipData = getScAddrOwnershipData(view, node.get.dataKey)
        .orElse(throw new ExecutionRevertedException("Could not find valid data"))

      // TODO we could use a different data serializer with just mc addr part
      val listItem = McAddrOwnershipData(ownershipData.get.scAddress, ownershipData.get.mcTransparentAddress)
      val prevNodeKey = node.get.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

  def initStateDb(view: BaseAccountStateView) : Unit = {
    if (view.getAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, listTipKey).sameElements(NULL_HEX_STRING_32))
      view.updateAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, listTipKey, listTipNullValue)

    // add the tip if this is a brand new sc address list
    if (view.getAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, scAddressTipValue).isEmpty) {
      view.updateAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, scAddressTipValue, listTipKey)
      ScAddressRefsLinkedList.addNewNode(view, scAddressTipValue, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
    }
  }

  def getOwnershipId(mcAddressStr: String): Array[Byte] =
    Blake2b256.hash(scAddressStringNoPrefix+mcAddressStr)
}

object ScAddrOwnershipLinkedList {

  def apply(view: BaseAccountStateView, scAddress: String): ScAddrOwnershipLinkedList = {
    val obj = new ScAddrOwnershipLinkedList(scAddress)
    obj.initStateDb(view)
    obj
  }


}
