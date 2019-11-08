package com.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

public class MCPublicKeyHash implements Proposition {

    public static final int KEY_LENGTH = 20;

    @JsonProperty("publicKey")
    private byte[] pubKeyHashBytes;

    public MCPublicKeyHash(byte[] bytes)
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
        return MCPublicKeyHashSerializer.getSerializer();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.pubKeyHashBytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof MCPublicKeyHash))
            return false;
        if (obj == this)
            return true;
        return Arrays.equals(this.pubKeyHashBytes, ((MCPublicKeyHash) obj).bytes());
    }

    public static MCPublicKeyHash parseBytes(byte[] bytes) {
        return new MCPublicKeyHash(bytes);
    }

    public static int getLength() {
        return KEY_LENGTH;
    }
}
