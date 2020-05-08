package com.horizen.vrf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class VrfOutput implements BytesSerializable {
    private final byte[] vrfOutputBytes;

    public VrfOutput(byte[] bytes) {
        vrfOutputBytes = Arrays.copyOf(bytes, bytes.length);
    }

    @JsonProperty("bytes")
    @Override
    public byte[] bytes() {
        return Arrays.copyOf(vrfOutputBytes, vrfOutputBytes.length);
    }

    @Override
    public ScorexSerializer serializer() {
        return VrfOutputSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VrfOutput vrfOutput = (VrfOutput) o;
        return Arrays.equals(vrfOutputBytes, vrfOutput.vrfOutputBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vrfOutputBytes);
    }

    public static VrfOutput parse(byte[] bytes) {
        return new VrfOutput(bytes);
    }

    @Override
    public String toString() {
        return "VrfOutput{" +
                "vrfOutputBytes=" + ByteUtils.toHexString(vrfOutputBytes) +
                '}';
    }
}
