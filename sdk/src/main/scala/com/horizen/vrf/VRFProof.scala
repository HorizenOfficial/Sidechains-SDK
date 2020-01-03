package com.horizen.vrf

class VRFProof(proof: Array[Byte]) {
  def proofToVRFHash(): Array[Byte] = ??? // jni call to Rust impl

  def bytes: Array[Byte] = proof
}

object VRFProof {
  val length: Int = 32 //just dummy number
  def parseBytes(bytes: Array[Byte]): VRFProof = new VRFProof(bytes)
}