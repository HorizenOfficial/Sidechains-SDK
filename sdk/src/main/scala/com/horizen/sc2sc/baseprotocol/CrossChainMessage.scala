package com.horizen.sc2sc.baseprotocol

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}


@JsonView(Array(classOf[Views.Default]))
case class CrossChainMessage(
                      messageType: Int,
                      senderSidechain:  Array[Byte],
                      sender: Array[Byte], //we keep it generic because the format is dependant on the sidechain type
                      receiverSidechain:  Array[Byte],
                      receiver: Array[Byte], //we keep it generic because  the format is dependant on the sidechain type
                      payload:  Array[Byte]
                            )
  extends BytesSerializable {

  override type M = CrossChainMessage

  override def serializer: SparkzSerializer[CrossChainMessage] = CrossChainMessageSerializer

  override def toString: String = "%s(senderSidechain: %s, sender: %s, receiverSidechain: %s, receiver: %s)"
    .format(this.getClass.toString, senderSidechain, sender, receiverSidechain, receiver)
}


