package com.horizen.vrf

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description
object VRFKeyGenerator {
  def generate(seed: Array[Byte]): (VRFSecretKey, VRFPublicKey) = {
    val secret = new VRFSecretKey(Array.fill(VRFSecretKey.length)(seed.deep.hashCode().toByte))
    val public = new VRFPublicKey(Array.fill(VRFPublicKey.length)(seed.deep.hashCode().toByte))

    (secret, public)
  } // jni call to Rust impl
}
