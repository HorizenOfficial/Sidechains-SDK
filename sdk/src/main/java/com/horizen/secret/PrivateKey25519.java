package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;

import com.horizen.utils.BytesUtils;
import com.horizen.utils.Ed25519;

import java.util.Arrays;

import static com.horizen.secret.SecretsIdsEnum.PrivateKey25519SecretId;


public final class PrivateKey25519 implements Secret
{
    public static final int KEY_LENGTH = Ed25519.keyLength();
    private static final byte privateKey25519SecretId = PrivateKey25519SecretId.id();

    private byte[] _privateKeyBytes;
    private byte[] _publicKeyBytes;

    public PrivateKey25519(byte[] privateKeyBytes, byte[] publicKeyBytes)
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
        return privateKey25519SecretId;
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(_privateKeyBytes, _publicKeyBytes);
    }

    @Override
    public SecretSerializer serializer() {
        return PrivateKey25519Serializer.getSerializer();
    }

    @Override
    public PublicKey25519Proposition publicImage() {
        return new PublicKey25519Proposition(_publicKeyBytes);
    }

    public static PrivateKey25519 parseBytes(byte[] bytes) {
        byte[] privateKeyBytes = Arrays.copyOf(bytes, KEY_LENGTH);
        byte[] publicKeyBytes = Arrays.copyOfRange(bytes, KEY_LENGTH, 2 * KEY_LENGTH);
        return new PrivateKey25519(privateKeyBytes, publicKeyBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrivateKey25519 that = (PrivateKey25519) o;
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
    public boolean owns(ProofOfKnowledgeProposition proposition) {
        return publicImage().equals(proposition);
    }

    @Override
    public Signature25519 sign(byte[] message) {
        return new Signature25519(Ed25519.sign(_privateKeyBytes, message, _publicKeyBytes));
    }

    public byte[] privateKey() {
        return Arrays.copyOf(_privateKeyBytes, KEY_LENGTH);
    }

    @Override
    public String toString() {
        return "PrivateKey25519{" +
                "_privateKeyBytes=" + BytesUtils.toHexString(_privateKeyBytes) +
                ", _publicKeyBytes=" + BytesUtils.toHexString(_publicKeyBytes) +
                '}';
    }
}
