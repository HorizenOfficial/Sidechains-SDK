package io.horizen.account.state

import io.horizen.cryptolibprovider.CircuitTypes.{NaiveThresholdSignatureCircuit, NaiveThresholdSignatureCircuitWithKeyRotation}
import io.horizen.params.NetworkParams
import io.horizen.utils.BytesUtils
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}

object MessageProcessorUtil {
  def getMessageProcessorSeq(params: NetworkParams, customMessageProcessors: Seq[MessageProcessor]): Seq[MessageProcessor] = {
    val maybeKeyRotationMsgProcessor = params.circuitType match {
      case NaiveThresholdSignatureCircuit => None
      case NaiveThresholdSignatureCircuitWithKeyRotation => Some(CertificateKeyRotationMsgProcessor(params))
    }
    Seq(
      EoaMessageProcessor,
      WithdrawalMsgProcessor,
      ForgerStakeMsgProcessor(params),
      McAddrOwnershipMsgProcessor(params),
    ) ++ maybeKeyRotationMsgProcessor.toSeq ++ customMessageProcessors
  }


  // A (sort of) linked list node containing:
  //     id of a data db record
  //     two keys to contiguous nodes, previous and next
  // Each node is stored in stateDb as key/value pair:
  //     key=Hash(node.dataKey) / value = node
  // Note:
  // 1) we use Blake256b hash since stateDb internally uses Keccak hash of stakeId as key for forger stake data records
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

}
