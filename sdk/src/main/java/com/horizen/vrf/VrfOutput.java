package com.horizen.vrf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.serialization.Views;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class VrfOutput implements BytesSerializable {
    public static final int OUTPUT_LENGTH = CryptoLibProvider.vrfFunctions().vrfOutputLen();

    @JsonProperty("bytes")
    final byte[] vrfOutputBytes;

    public VrfOutput(byte[] bytes) {
        if (bytes.length != OUTPUT_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect output length, %d expected, %d found", OUTPUT_LENGTH,
                    bytes.length));

        vrfOutputBytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return serializer().toBytes(this);
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

    @Override
    public String toString() {
        return "VrfOutput{" +
                "vrfOutputBytes=" + ByteUtils.toHexString(vrfOutputBytes) +
                '}';
    }
}
