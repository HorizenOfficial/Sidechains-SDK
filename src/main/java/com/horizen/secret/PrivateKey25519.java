package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;

import com.horizen.utils.BytesUtils;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;


public final class PrivateKey25519 implements Secret
{
    public static final int KEY_LENGTH = Curve25519.KeyLength();
    public static final byte SECRET_TYPE_ID = 0;

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
        return SECRET_TYPE_ID;
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

    public static Try<PrivateKey25519> parseBytes(byte[] bytes) {
        try {
            byte[] privateKeyBytes = Arrays.copyOf(bytes, KEY_LENGTH);
            byte[] publicKeyBytes = Arrays.copyOfRange(bytes, KEY_LENGTH, 2 * KEY_LENGTH);
            PrivateKey25519 secret = new PrivateKey25519(privateKeyBytes, publicKeyBytes);
            return new Success<PrivateKey25519>(secret);
        } catch (Exception e) {
            return new Failure(e);
        }
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
        return new Signature25519(Curve25519.sign(_privateKeyBytes, message));
    }

    @Override
    public String toString() {
        return "PrivateKey25519{" +
                "_privateKeyBytes=" + BytesUtils.toHexString(_privateKeyBytes) +
                ", _publicKeyBytes=" + BytesUtils.toHexString(_publicKeyBytes) +
                '}';
    }
}
