package com.horizen.sc2sc

import java.nio.charset.StandardCharsets

import com.horizen.sc2sc.baseprotocol.CrossChainMessage
import com.horizen.utils.BytesUtils
import sparkz.core.transaction.box.proposition.Proposition

trait CrossChainMessageFixture {

  val MessageType = 1
  val senderSidechainIdHex: String = "262f60319a17f61613b137c1e7ef0a98d5e378843f6766dceb1fc149acee68e8"
  val receiverSidechainIdHex: String = "d5e378843f6766d262f60319a17f61613b137149acee68e8c1e7ef0a98ceb1fc"
  val payloadString = "this is a message"

  def getCrossChainMessage(sender: Proposition, receiver: Proposition) : CrossChainMessage = {
    CrossChainMessage(MessageType,
      BytesUtils.fromHexString(senderSidechainIdHex), sender.bytes,
      BytesUtils.fromHexString(receiverSidechainIdHex), receiver.bytes,
      payloadString.getBytes(StandardCharsets.UTF_8)
    )
  }

}
