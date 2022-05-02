package com.horizen.customtypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.ScorexEncoding;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PropositionSerializer;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;

@JsonView(Views.Default.class)
public class CustomPublicKeyProposition extends ScorexEncoding implements ProofOfKnowledgeProposition<CustomPrivateKey>
{
    public static final int PUBLIC_KEY_LENGTH = 128;

    @JsonProperty("publicKey")
    private byte[] pubKeyBytes;

    public CustomPublicKeyProposition (byte[] pubKeyBytes) {
        if(pubKeyBytes.length != PUBLIC_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", PUBLIC_KEY_LENGTH, pubKeyBytes.length));

        this.pubKeyBytes = Arrays.copyOf(pubKeyBytes, PUBLIC_KEY_LENGTH);
    }

    @Override
    public PropositionSerializer serializer() {
        return CustomPublicKeyPropositionSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomPublicKeyProposition that = (CustomPublicKeyProposition) o;
        return Arrays.equals(pubKeyBytes, that.pubKeyBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pubKeyBytes);
    }

    public static int getLength() {
        return PUBLIC_KEY_LENGTH;
    }

    @Override
    public String toString() {
        return "CustomPublicKeyProposition{" +
                "pubKeyBytes=" + BytesUtils.toHexString(pubKeyBytes) +
                '}';
    }

    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(pubKeyBytes, PUBLIC_KEY_LENGTH);
    }
}
