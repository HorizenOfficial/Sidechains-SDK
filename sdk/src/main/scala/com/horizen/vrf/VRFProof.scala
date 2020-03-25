package com.horizen.vrf

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description

class VRFProof(proof: Array[Byte]) {
  def proofToVRFHash(): Array[Byte] = {
    require(proof.length == VRFProof.length)
    VrfLoader.vrfFunctions.proofBytesToVrfHashBytes(proof)
    //val xorByte = (proof.head ^ proof.last).toByte
    //proof.map(b => (b ^ xorByte).toByte)
  } // jni call to Rust impl

  def bytes: Array[Byte] = proof
}

object VRFProof {
  val length: Int = 32 //sha256HashLen
  def parseBytes(bytes: Array[Byte]): VRFProof = new VRFProof(bytes)
}