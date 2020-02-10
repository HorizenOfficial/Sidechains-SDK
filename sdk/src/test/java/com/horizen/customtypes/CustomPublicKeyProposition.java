package com.horizen.customtypes;

import com.horizen.ScorexEncoding;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PropositionSerializer;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;

public class CustomPublicKeyProposition extends ScorexEncoding implements ProofOfKnowledgeProposition<CustomPrivateKey>
{

    private static final int KEY_LENGTH = 128;
    private byte[] _pubKeyBytes;

    public CustomPublicKeyProposition (byte[] pubKeyBytes) {
        if(pubKeyBytes.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", KEY_LENGTH, pubKeyBytes.length));

        _pubKeyBytes = Arrays.copyOf(pubKeyBytes, KEY_LENGTH);
    }

    @Override
    public byte[] bytes() {
        return _pubKeyBytes;
    }

    @Override
    public PropositionSerializer serializer() {
        return CustomPublicKeyPropositionSerializer.getSerializer();
    }

    public static CustomPublicKeyProposition parseBytes(byte[] bytes) {
        return new CustomPublicKeyProposition(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomPublicKeyProposition that = (CustomPublicKeyProposition) o;
        return Arrays.equals(_pubKeyBytes, that._pubKeyBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_pubKeyBytes);
    }

    public static int getLength() {
        return KEY_LENGTH;
    }

    @Override
    public String toString() {
        return "CustomPublicKeyProposition{" +
                "_pubKeyBytes=" + BytesUtils.toHexString(_pubKeyBytes) +
                '}';
    }

    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(_pubKeyBytes, KEY_LENGTH);
    }
}
