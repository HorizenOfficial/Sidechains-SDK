package com.horizen.vrf;

import com.horizen.utils.Pair;

public class VRFKeyGenerator {

  public static Pair<VRFSecretKey, VRFPublicKey> generate(byte[] seed) {
    return new Pair(new VRFSecretKey(new byte[32]) , new VRFPublicKey(new byte[32]));
  } // jni call to Rust impl
}
