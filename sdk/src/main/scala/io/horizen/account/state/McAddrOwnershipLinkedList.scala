package io.horizen.account.state

import io.horizen.account.state.MessageProcessorUtil.{LinkedListNode, LinkedListNodeSerializer}
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS
import io.horizen.utils.BytesUtils
import org.web3j.utils.Numeric
import sparkz.crypto.hash.Blake2b256

import scala.util.{Failure, Success}

object McAddrOwnershipLinkedList {

  val LinkedListTipKey: Array[Byte] = Blake2b256.hash("Tip")
  val LinkedListNullValue: Array[Byte] = Blake2b256.hash("Null")

  def findLinkedListNode(view: BaseAccountStateView, nodeId: Array[Byte]): Option[LinkedListNode] = {
    val data = view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, nodeId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      LinkedListNodeSerializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger info.", exception)
      }
    }
  }

  def addNewNodeToList(view: BaseAccountStateView, ownershipId: Array[Byte]): Unit = {
    val oldTip = view.getAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, LinkedListTipKey)

    val newTip = Blake2b256.hash(ownershipId)

    // modify previous node (if any) to point at this one
    modifyNode(view, oldTip) { previousNode =>
      LinkedListNode(previousNode.dataKey, previousNode.previousNodeKey, newTip)
    }

    // update list tip, now it is this newly added one
    view.updateAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, LinkedListTipKey, newTip)

    // store the new node
    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, newTip,
      LinkedListNodeSerializer.toBytes(
        LinkedListNode(ownershipId, oldTip, LinkedListNullValue)))
  }

  def modifyNode(view: BaseAccountStateView, nodeId: Array[Byte])(
    modify: LinkedListNode => LinkedListNode
  ): Option[Unit] = {
    if (linkedListNodeRefIsNull(nodeId)) return None
    // find original node
    findLinkedListNode(view, nodeId)
      // if the node was not found we want to revert execution
      .orElse(throw new ExecutionRevertedException(s"Failed to update node: ${Numeric.toHexString(nodeId)}"))
      // modify node
      .map(modify)
      // serialize modified node
      .map(LinkedListNodeSerializer.toBytes)
      // overwrite the modified node
      .map(view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, nodeId, _))
  }

  def findMcAddrOwnershipsData(view: BaseAccountStateView, ownershipId: Array[Byte]): Option[McAddrOwnershipData] = {
    val data = view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, ownershipId)
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

  def getListItem(view: BaseAccountStateView, tip: Array[Byte]): (AccountForgingStakeInfo, Array[Byte]) = {
    /*if (!linkedListNodeRefIsNull(tip)) {
      val node = findLinkedListNode(view, tip).get
      val stakeData = findStakeData(view, node.dataKey).get
      val listItem = AccountForgingStakeInfo(
        node.dataKey,
        McAddrOwnershipData(
          ForgerPublicKeys(
            stakeData.forgerPublicKeys.blockSignPublicKey, stakeData.forgerPublicKeys.vrfPublicKey),
          stakeData.ownerPublicKey, stakeData.stakedAmount)
      )
      val prevNodeKey = node.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }

     */
    ???
  }

  def linkedListNodeRefIsNull(ref: Array[Byte]): Boolean =
    BytesUtils.toHexString(ref).equals(BytesUtils.toHexString(LinkedListNullValue))
}
