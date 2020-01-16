package com.horizen.vrf;

import com.horizen.utils.BytesUtils;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;

public class VRFSecretKey
    implements BytesSerializable
{

    public static final int KEY_LENGTH = 32;

    private byte[] key;

    public VRFSecretKey(byte[] bytes) {
        if (bytes.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect public key length, %d expected, %d found", KEY_LENGTH, bytes.length));
        this.key = bytes;
    }

    private native byte[] nativeProve (byte[] key, byte[] message); // jni call to Rust impl

    public VRFProof prove(byte[] message) {
        return new VRFProof(nativeProve(this.key, message));
    }

    private native byte[] nativeVRFHash(byte[] key, byte[] message); // if need // jni call to Rust impl

    public byte[] vrfHash(byte[] message) {
        return nativeVRFHash(key, message);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(this.key, KEY_LENGTH);
    }

    @Override
    public ScorexSerializer serializer() {
        return VRFSecretKeySerializer.getSerializer();
    }

    public static VRFSecretKey parseBytes(byte[] bytes) {
        return new VRFSecretKey(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VRFSecretKey that = (VRFSecretKey) o;
        return Arrays.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(key);
    }

    @Override
    public String toString() {
        return "VRFSecretKey{" +
                "key=" + BytesUtils.toHexString(key) +
                '}';
    }
}
