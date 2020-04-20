package com.horizen.vrf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class VrfProofHash implements BytesSerializable {
    private final byte[] proofHashBytes;

    public VrfProofHash(byte[] bytes) {
        proofHashBytes = Arrays.copyOf(bytes, bytes.length);
    }

    @JsonProperty("bytes")
    @Override
    public byte[] bytes() {
        return Arrays.copyOf(proofHashBytes, proofHashBytes.length);
    }

    @Override
    public ScorexSerializer serializer() {
        return VrfProofHashSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VrfProofHash vrfProofHash = (VrfProofHash) o;
        return Arrays.equals(proofHashBytes, vrfProofHash.proofHashBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(proofHashBytes);
    }

    public static VrfProofHash parse(byte[] bytes) {
        return new VrfProofHash(bytes);
    }

    @Override
    public String toString() {
        return "VrfProofHash{" +
                "proofHashBytes=" + Arrays.toString(proofHashBytes) +
                '}';
    }
}
