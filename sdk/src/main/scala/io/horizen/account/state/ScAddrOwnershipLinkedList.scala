package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.NativeSmartContractLinkedList
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import sparkz.crypto.hash.Blake2b256

import java.nio.charset.StandardCharsets

class ScAddrOwnershipLinkedList(scAddressStringNoPrefix : String)
  extends NativeSmartContractLinkedList {

  val listTipKey: Array[Byte] = Blake2b256.hash(scAddressStringNoPrefix+"TipKey")
  val listTipNullValue: Array[Byte] = Blake2b256.hash(scAddressStringNoPrefix+"TipNullValue")

  val scAddressTipValue : Array[Byte] = Blake2b256.hash(scAddressStringNoPrefix)

  private def getScAddrData(view: BaseAccountStateView, dataKey: Array[Byte]): Option[String] = {
    val data = view.getAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, dataKey)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      Some(new String(data, StandardCharsets.UTF_8))
    }
  }

  def getItem(view: BaseAccountStateView, nodeRef: Array[Byte]): (String, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(nodeRef)) {

      val node = getLinkedListNode(view, nodeRef, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
        .orElse(throw new ExecutionRevertedException("Could not find a valid node"))

      val data = getScAddrData(view, node.get.dataKey)
        .orElse(throw new ExecutionRevertedException("Could not find valid data"))

      val prevNodeKey = node.get.previousNodeKey
      (data.get, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

  private def initStateDb(view: BaseAccountStateView, scAddressStr: String): Unit = {
    if (view.getAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, listTipKey).sameElements(NULL_HEX_STRING_32)) {
      view.updateAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, listTipKey, listTipNullValue)
    }

    // add this sc address to the refs linked list if this is a brand new sc address list
    if (view.getAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, scAddressTipValue).isEmpty) {
      ScAddressRefsLinkedList.addNewNode(view, scAddressTipValue, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
      view.updateAccountStorageBytes(
        MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS,
        scAddressTipValue,
        OwnerScAddressSerializer.toBytes(OwnerScAddress(scAddressStr)))
    }
  }

  def getDataId(mcAddressStr: String): Array[Byte] =
    Blake2b256.hash(scAddressStringNoPrefix+mcAddressStr)

  def getTip(view: BaseAccountStateView): Array[Byte] =
    view.getAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, listTipKey)

  override def removeNode(view: BaseAccountStateView, dataId: Array[Byte],
                          contract_address: Address): Unit = {
    super.removeNode(view, dataId, contract_address)
    // if this is the very last node, clean it up
    if (view.getAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, listTipKey).sameElements(listTipNullValue)) {
      view.removeAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, listTipKey)
      view.removeAccountStorageBytes(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, scAddressTipValue)
      ScAddressRefsLinkedList.removeNode(view, scAddressTipValue, MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS)
    }
  }


  }

object ScAddrOwnershipLinkedList {

  def apply(view: BaseAccountStateView, scAddress: String): ScAddrOwnershipLinkedList = {
    val obj = new ScAddrOwnershipLinkedList(scAddress)
    obj.initStateDb(view, scAddress)
    obj
  }
}
