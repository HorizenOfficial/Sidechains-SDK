package com.horizen.sc2sc.baseprotocol
import scorex.util.serialization.{Reader, Writer}
import sparkz.core.serialization.SparkzSerializer

object CrossChainRedeemMessageSerializer extends SparkzSerializer[CrossChainRedeemMessage] {
  override def serialize(s: CrossChainRedeemMessage, w: Writer): Unit = {
    CrossChainMessageSerializer.serialize(s.message, w)
    w.putInt(s.certificateDataHash.length)
    w.putBytes(s.certificateDataHash)
    w.putInt(s.nextCertificateDataHash.length)
    w.putBytes(s.nextCertificateDataHash)
    w.putInt(s.scCommitmentTreeRoot.length)
    w.putBytes(s.scCommitmentTreeRoot)
    w.putInt(s.nextScCommitmentTreeRoot.length)
    w.putBytes(s.nextScCommitmentTreeRoot)
    w.putInt(s.proof.length)
    w.putBytes(s.proof)
  }

  override def parse(r: Reader): CrossChainRedeemMessage = {
    val message = CrossChainMessageSerializer.parse(r)
    val certificateDataHash = r.getBytes(r.getInt())
    val nextCertificateDataHash = r.getBytes(r.getInt())
    val scCommitmentTreeRoot = r.getBytes(r.getInt())
    val nextScCommitmentTreeRoot = r.getBytes(r.getInt())
    val proof = r.getBytes(r.getInt())
    CrossChainRedeemMessage(message, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot, nextScCommitmentTreeRoot, proof)
  }
}