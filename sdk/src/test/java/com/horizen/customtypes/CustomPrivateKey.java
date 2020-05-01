package com.horizen.customtypes;

import com.google.common.primitives.Bytes;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretSerializer;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;

public class CustomPrivateKey implements Secret
{
    public static final int KEY_LENGTH = 128;
    public static final byte SECRET_TYPE_ID = 1;

    private byte[] _privateKeyBytes;
    private byte[] _publicKeyBytes;

    public CustomPrivateKey(byte[] privateKeyBytes, byte[] publicKeyBytes)
    {
        if(privateKeyBytes.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", KEY_LENGTH,
                    privateKeyBytes.length));
        if(publicKeyBytes.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", KEY_LENGTH,
                    publicKeyBytes.length));

        _privateKeyBytes = Arrays.copyOf(privateKeyBytes, KEY_LENGTH);
        _publicKeyBytes = Arrays.copyOf(publicKeyBytes, KEY_LENGTH);
    }

    @Override
    public byte secretTypeId() {
        return SECRET_TYPE_ID;
    }

    @Override
    public CustomPublicKeyProposition publicImage() {
        return new CustomPublicKeyProposition(_publicKeyBytes);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(_privateKeyBytes, _publicKeyBytes);
    }

    @Override
    public SecretSerializer serializer() {
        return CustomPrivateKeySerializer.getSerializer();
    }

    @Override
    public boolean owns(ProofOfKnowledgeProposition proposition) {
        return  publicImage().equals(proposition);
    }

    @Override
    public ProofOfKnowledge sign(byte[] message) {
        return null;
    }

    public static CustomPrivateKey parseBytes(byte[] bytes) {
        byte[] privateKeyBytes = Arrays.copyOf(bytes, KEY_LENGTH);
        byte[] publicKeyBytes = Arrays.copyOfRange(bytes, KEY_LENGTH, 2 * KEY_LENGTH);
        return new CustomPrivateKey(privateKeyBytes, publicKeyBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomPrivateKey that = (CustomPrivateKey) o;
        return Arrays.equals(_privateKeyBytes, that._privateKeyBytes) &&
                Arrays.equals(_publicKeyBytes, that._publicKeyBytes);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(_privateKeyBytes);
        result = 31 * result + Arrays.hashCode(_publicKeyBytes);
        return result;
    }

    @Override
    public String toString() {
        return "CustomPrivateKey{" +
                "_privateKeyBytes=" + BytesUtils.toHexString(_privateKeyBytes) +
                ", _publicKeyBytes=" + BytesUtils.toHexString(_publicKeyBytes) +
                '}';
    }
}
