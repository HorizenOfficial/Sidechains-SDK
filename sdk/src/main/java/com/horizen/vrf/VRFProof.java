package com.horizen.vrf;

import com.horizen.utils.BytesUtils;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.lang.reflect.Array;
import java.util.Arrays;

public class VRFProof
    implements BytesSerializable
{

    public static final int PROOF_LENGTH = 32;

    private byte[] proof;

    public VRFProof(byte[] bytes) {
        if (bytes.length != PROOF_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect proof length, %d expected, %d found", PROOF_LENGTH, bytes.length));
        this.proof = bytes;
    }

    private native byte[] nativeProofToVRFHash (byte[] proof); // jni call to Rust impl

    public byte[] proofToVRFHash() {
        return nativeProofToVRFHash(this.proof);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(this.proof, PROOF_LENGTH);
    }

    @Override
    public ScorexSerializer serializer() {
        return VRFProofSerializer.getSerializer();
    }

    public static VRFProof parseBytes(byte[] bytes) {
        return new VRFProof(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VRFProof vrfProof = (VRFProof) o;
        return Arrays.equals(proof, vrfProof.proof);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(proof);
    }

    @Override
    public String toString() {
        return "VRFProof{" +
                "proof=" + BytesUtils.toHexString(proof) +
                '}';
    }
}