package io.horizen.account.state

import io.horizen.cryptolibprovider.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.evm.Address
import io.horizen.params.{NetworkParams, RegTestParams}
import io.horizen.utils.BytesUtils
import org.web3j.utils.Numeric
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.crypto.hash.Blake2b256
import sparkz.util.serialization.{Reader, Writer}

import scala.util.{Failure, Success}

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor]): Seq[MessageProcessor] = {
    val maybeKeyRotationMsgProcessor = params.circuitType match {
      case NaiveThresholdSignatureCircuit => None
      case NaiveThresholdSignatureCircuitWithKeyRotation => Some(CertificateKeyRotationMsgProcessor(params))
    }

    val maybeProxyMsgProcessor = params match {
      case _ : RegTestParams => Some(ProxyMsgProcessor(params))
      case _ => None
    }

    // Since fork dependant native smart contract are not initialized at genesis state, their msg
    // processor must be placed before the Eoa msg processor.
    // This is for having the initialization performed as soon as the fork point is reached, otherwise
    // the Eoa msg processor would preempt it

    Seq(McAddrOwnershipMsgProcessor(params)) ++
    maybeProxyMsgProcessor.toSeq ++
    Seq(ForgerStakeV2MsgProcessor) ++
    Seq(EoaMessageProcessor,
        WithdrawalMsgProcessor,
        ForgerStakeMsgProcessor(params),
      ) ++
      maybeKeyRotationMsgProcessor.toSeq ++
      customMessageProcessors
  }


  // A (sort of) linked list node containing:
  //     id of a data db record
  //     two keys to contiguous nodes, previous and next
  // Each node is stored in stateDb as key/value pair:
  //     key=Hash(node.dataKey) / value = node
  // Note:
  // 1) we use Blake256b hash since stateDb internally uses Keccak hash of dataId as key for data records
  // and it would clash
  // 2) TIP value is stored in the state db as well, initialized as NULL value

  /*
  TIP                        NULL
    |                          ^
    |                           \
    |                            \
    +-----> NODE_n [ d_n, prev, next]  <-------+
                      |    |                    \
                      |    |                     \
                      |    V                      \
                      |  NODE_n-1 [id_n-1, prev, next]  <-------+
                      |              |      |                    \
                      |              |      |                     \
                      V              |      V                      \
                    DATA_n           |    NODE_n-2 [id_n-2, prev, next]
                                     |                |      |
                                     |                |      |
                                     |               ...    ...
                                     V
                                   DATA_n-1                 .
                                                             .
                                                              .
                                                         ...               ...
                                                          |                  \
                                                          V                   \
                                                       NODE_1 [id_n-1, prev, next]  <----+
                                                                 |      |                 \
                                                                 |      |                  \
                                                                 |      V                   \
                                                                 |    NODE_0 [id_0, prev, next]
                                                                 |              |    |
                                                                 |              |    |
                                                                 V              |    V
                                                               DATA_1           |   NULL
                                                                                |
                                                                                |
                                                                                V
                                                                              DATA_0

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

  trait NativeSmartContractLinkedList {

    val listTipKey: Array[Byte]
    val listTipNullValue: Array[Byte]

    def getLinkedListNode(view: BaseAccountStateView, nodeId: Array[Byte], contract_address: Address): Option[LinkedListNode] = {
      val data = view.getAccountStorageBytes(contract_address, nodeId)
      if (data.length == 0) {
        // getting a not existing key from state DB using RAW strategy
        // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
        // gives an empty array instead
        None
      } else {
        LinkedListNodeSerializer.parseBytesTry(data) match {
          case Success(obj) => Some(obj)
          case Failure(exception) =>
            throw new ExecutionRevertedException("Error while parsing data.", exception)
        }
      }
    }

    def addNewNode(view: BaseAccountStateView, dataId: Array[Byte],
                   contract_address: Address): Unit = {
      val oldTip = view.getAccountStorage(contract_address, listTipKey)

      val nodeToAddId = Blake2b256.hash(dataId)

      // modify previous node (if any) to point at this one
      modifyNode(view, oldTip, contract_address) { previousNode =>
        LinkedListNode(previousNode.dataKey, previousNode.previousNodeKey, nodeToAddId)
      }

      // update list tip, now it is this newly added one
      view.updateAccountStorage(contract_address, listTipKey, nodeToAddId)

      // store the new node
      view.updateAccountStorageBytes(contract_address, nodeToAddId,
        LinkedListNodeSerializer.toBytes(
          LinkedListNode(dataId, oldTip, listTipNullValue)))
    }

    def modifyNode(view: BaseAccountStateView, nodeId: Array[Byte], contract_address: Address)(
      modify: LinkedListNode => LinkedListNode
    ): Option[Unit] = {
      if (linkedListNodeRefIsNull(nodeId)) return None
      // find original node
      getLinkedListNode(view, nodeId, contract_address)
        // if the node was not found we want to revert execution
        .orElse(throw new ExecutionRevertedException(s"Failed to update node: ${Numeric.toHexString(nodeId)}"))
        // modify node
        .map(modify)
        // serialize modified node
        .map(LinkedListNodeSerializer.toBytes)
        // overwrite the modified node
        .map(view.updateAccountStorageBytes(contract_address, nodeId, _))
    }

    def removeNode(view: BaseAccountStateView, dataId: Array[Byte],
                   contract_address: Address): Unit = {

      // we assume that the caller have checked that the data really exists in the stateDb.
      val nodeToRemoveId = Blake2b256.hash(dataId)

      val nodeToRemove = getLinkedListNode(view, nodeToRemoveId, contract_address).get

      // modify previous node if any
      modifyNode(view, nodeToRemove.previousNodeKey, contract_address) { previousNode =>
        LinkedListNode(previousNode.dataKey, previousNode.previousNodeKey, nodeToRemove.nextNodeKey)
      }

      // modify next node if any
      modifyNode(view, nodeToRemove.nextNodeKey, contract_address) { nextNode =>
        LinkedListNode(nextNode.dataKey, nodeToRemove.previousNodeKey, nextNode.nextNodeKey)
      } getOrElse {
        // if there is no next node, we update the linked list tip to point to the previous node, promoted to be the new tip
        view.updateAccountStorage(contract_address, listTipKey, nodeToRemove.previousNodeKey)
      }

      // remove the node from the storage
      view.removeAccountStorageBytes(contract_address, nodeToRemoveId)
    }

    def linkedListNodeRefIsNull(ref: Array[Byte]): Boolean =
      ref.sameElements(listTipNullValue)

  }
}
