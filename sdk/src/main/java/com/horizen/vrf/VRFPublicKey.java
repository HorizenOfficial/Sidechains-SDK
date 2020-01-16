package com.horizen.vrf;

import com.horizen.utils.BytesUtils;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;

public class VRFPublicKey
    implements BytesSerializable
{

  public static final int KEY_LENGTH = 32;

  private byte[] key;

  public VRFPublicKey(byte[] bytes) {
    if (bytes.length != KEY_LENGTH)
      throw new IllegalArgumentException(String.format("Incorrect public key length, %d expected, %d found", KEY_LENGTH, bytes.length));
    this.key = bytes;
  }

  private native boolean nativeVerify (byte[] key, byte[] message, byte[] proof); // jni call to Rust impl

  boolean verify(byte[] message, byte[] proof) {
    return nativeVerify(this.key, message, proof);
  }

  @Override
  public byte[] bytes() {
    return Arrays.copyOf(this.key, KEY_LENGTH);
  }

  @Override
  public ScorexSerializer serializer() {
    return VRFPublicKeySerializer.getSerializer();
  }

  public static VRFPublicKey parseBytes(byte[] bytes) {
    return new VRFPublicKey(bytes);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VRFPublicKey that = (VRFPublicKey) o;
    return Arrays.equals(key, that.key);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(key);
  }

  @Override
  public String toString() {
    return "VRFPublicKey{" +
            "key=" + BytesUtils.toHexString(key) +
            '}';
  }

  // maybe also a method for verifying VRFPublicKey
  //def isValid: Boolean = ??? // jni call to Rust impl

}

