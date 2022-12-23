package com.horizen.sc2sc.baseprotocol

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}

@JsonView(Array(classOf[Views.Default]))
case class CrossChainRedeemMessage(
                            message: CrossChainMessage,
                            certificateDataHash:  Array[Byte],
                            nextCertificateDataHash: Array[Byte],
                            scCommitmentTreeRoot:  Array[Byte],
                            nextScCommitmentTreeRoot: Array[Byte],
                            proof:  Array[Byte]
                            )
  extends BytesSerializable {

  override type M = CrossChainRedeemMessage

  override def serializer: SparkzSerializer[CrossChainRedeemMessage] = CrossChainRedeemMessageSerializer


}


