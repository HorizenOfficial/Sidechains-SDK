package com.horizen.sc2sc.baseprotocol

import com.horizen.account.proposition.AddressPropositionSerializer
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.SparkzSerializer

object CrossChainMessageSerializer extends SparkzSerializer[CrossChainMessage] {
  override def serialize(s: CrossChainMessage, w: Writer): Unit = {
    w.putInt(s.messageType)
    w.putInt(s.senderSidechain.length)
    w.putBytes(s.senderSidechain)
    w.putInt(s.sender.length)
    w.putBytes(s.sender)
    w.putInt(s.receiverSidechain.length)
    w.putBytes(s.receiverSidechain)
    w.putInt(s.receiver.length)
    w.putBytes(s.receiver)
    w.putInt(s.payload.length)
    w.putBytes(s.payload)
  }

  override def parse(r: Reader): CrossChainMessage = {
    val msgType = r.getInt()
    val senderSidechain = r.getBytes(r.getInt())
    val sender = r.getBytes(r.getInt())
    val receiverSidechain = r.getBytes(r.getInt())
    val receiver =r.getBytes(r.getInt())
    val payload = r.getBytes(r.getInt())
    CrossChainMessage(msgType, senderSidechain, sender, receiverSidechain, receiver, payload)
  }
}