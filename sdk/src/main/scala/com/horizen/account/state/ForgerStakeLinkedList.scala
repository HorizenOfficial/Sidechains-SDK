package com.horizen.account.state

import com.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS
import com.horizen.utils.BytesUtils
import org.web3j.utils.Numeric
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.crypto.hash.Blake2b256
import sparkz.util.serialization.{Reader, Writer}

import scala.util.{Failure, Success}

object ForgerStakeLinkedList {

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

  def addNewNodeToList(view: BaseAccountStateView, stakeId: Array[Byte]): Unit = {
    val oldTip = view.getAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, LinkedListTipKey)

    val newTip = Blake2b256.hash(stakeId)

    // modify previous node (if any) to point at this one
    modifyNode(view, oldTip) { previousNode =>
      LinkedListNode(previousNode.dataKey, previousNode.previousNodeKey, newTip)
    }

    // update list tip, now it is this newly added one
    view.updateAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, LinkedListTipKey, newTip)

    // store the new node
    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, newTip,
      LinkedListNodeSerializer.toBytes(
        LinkedListNode(stakeId, oldTip, LinkedListNullValue)))
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

  def findStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeData] = {
    val data = view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      ForgerStakeDataSerializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger data.", exception)
      }
    }
  }

  def getListItem(view: BaseAccountStateView, tip: Array[Byte]): (AccountForgingStakeInfo, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(tip)) {
      val node = findLinkedListNode(view, tip).get
      val stakeData = findStakeData(view, node.dataKey).get
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

  def linkedListNodeRefIsNull(ref: Array[Byte]): Boolean =
    BytesUtils.toHexString(ref).equals(BytesUtils.toHexString(LinkedListNullValue))
}

// A (sort of) linked list node containing:
//     stakeId of a forger stake data db record
//     two keys to contiguous nodes, previous and next
// Each node is stored in stateDb as key/value pair:
//     key=Hash(node.dataKey) / value = node
// Note:
// 1) we use Blake256b hash since stateDb internally uses Keccak hash of stakeId as key for forger stake data records
// and it would clash
// 2) TIP value is stored in the state db as well, initialized as NULL value

/*
TIP                            NULL
  |                               ^
  |                                \
  |                                 \
  +-----> NODE_n [stakeId_n, prev, next]  <------------+
                    |         |                         \
                    |         |                          \
                    |         V                           \
                    |       NODE_n-1 [stakeId_n-1, prev, next]  <------------+
                    |                   |           |                         \
                    |                   |           |                          \
                    V                   |           V                           \
                 STAKE_n                |         NODE_n-2 [stakeId_n-2, prev, next]
                                        |                     |           |
                                        |                    ...         ...
                                        V
                                     STAKE_n-1                      .
                                                                     .
                                                                      .

                                                                                    ...
                                                                                      \
                                                          NODE_1 [stakeId_n-1, prev, next]  <---------+
                                                                    |           |                      \
                                                                    |           |                       \
                                                                    |           V                        \
                                                                    |         NODE_0 [stakeId_0, prev, next]
                                                                    |                   |         |
                                                                    |                   |         |
                                                                    V                   |         V
                                                                 STAKE_1                |       NULL
                                                                                        |
                                                                                        |
                                                                                        V
                                                                                     STAKE_0

 */

case class LinkedListNode(dataKey: Array[Byte], previousNodeKey: Array[Byte], nextNodeKey: Array[Byte])
  extends BytesSerializable {

  require(dataKey.length == 32, "data key size should be 32")
  require(previousNodeKey.length == 32, "next node key size should be 32")
  require(nextNodeKey.length == 32, "next node key size should be 32")

  override type M = LinkedListNode

  override def serializer: SparkzSerializer[LinkedListNode] = LinkedListNodeSerializer

  override def toString: String = "%s(dataKey: %s, previousNodeKey: %s, nextNodeKey: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(dataKey),
      BytesUtils.toHexString(previousNodeKey), BytesUtils.toHexString(nextNodeKey))
}

object LinkedListNodeSerializer extends SparkzSerializer[LinkedListNode] {
  override def serialize(s: LinkedListNode, w: Writer): Unit = {
    w.putBytes(s.dataKey)
    w.putBytes(s.previousNodeKey)
    w.putBytes(s.nextNodeKey)
  }

  override def parse(r: Reader): LinkedListNode = {
    val dataKey = r.getBytes(32)
    val previousNodeKey = r.getBytes(32)
    val nextNodeKey = r.getBytes(32)
    LinkedListNode(dataKey, previousNodeKey, nextNodeKey)
  }
}
