package com.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;

@JsonView(Views.Default.class)
public final class MCPublicKeyHashProposition implements Proposition {

    public static final int KEY_LENGTH = 20;

    @JsonProperty("publicKey")
    private byte[] pubKeyHashBytes;

    public MCPublicKeyHashProposition(byte[] bytes)
    {
        if(bytes.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKeyHash length, %d expected, %d found", KEY_LENGTH, bytes.length));

        this.pubKeyHashBytes = Arrays.copyOf(bytes, KEY_LENGTH);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(this.pubKeyHashBytes, KEY_LENGTH);
    }

    @Override
    public PropositionSerializer serializer() {
        return MCPublicKeyHashPropositionSerializer.getSerializer();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.pubKeyHashBytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof MCPublicKeyHashProposition))
            return false;
        if (obj == this)
            return true;
        return Arrays.equals(this.pubKeyHashBytes, ((MCPublicKeyHashProposition) obj).bytes());
    }

    @Override
    public String toString() {
        return BytesUtils.toHexString(pubKeyHashBytes);
    }

    public static MCPublicKeyHashProposition parseBytes(byte[] bytes) {
        return new MCPublicKeyHashProposition(bytes);
    }

    public static int getLength() {
        return KEY_LENGTH;
    }
}
