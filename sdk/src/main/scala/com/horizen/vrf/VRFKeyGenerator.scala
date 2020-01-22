package com.horizen.vrf

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description
object VRFKeyGenerator {
  def generate(seed: Array[Byte]): (VRFSecretKey, VRFPublicKey) = {
    (new VRFSecretKey(Array.fill(VRFSecretKey.length)(seed.deep.hashCode().toByte)), new VRFPublicKey(Array.fill(VRFSecretKey.length)(seed.deep.hashCode().toByte)))
  } // jni call to Rust impl
}
