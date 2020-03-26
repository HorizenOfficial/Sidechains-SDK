package com.horizen.vrf

// See https://tools.ietf.org/id/draft-goldbe-vrf-01.html#rfc.section.2 as functions description
object VRFKeyGenerator {
  def generate(seed: Array[Byte]): (VRFSecretKey, VRFPublicKey) = {
    val bytes = VrfLoader.vrfFunctions.generate(seed)
    val secret = new VRFSecretKey(bytes.get(0))
    val public = new VRFPublicKey(bytes.get(1))

    (secret, public)
  } // jni call to Rust impl
}
