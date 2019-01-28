package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.horizen.proposition.PublicKey25519Proposition;

import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;


public final class PrivateKey25519 implements Secret
{
    public static final int KEY_LENGTH = Curve25519.KeyLength();

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
        return 0;
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

    public byte[] privateKeyBytes() {
        return  Arrays.copyOf(_privateKeyBytes, KEY_LENGTH);
    }

    @Override
    public PrivateKey25519Companion companion() {
        return PrivateKey25519Companion.getCompanion();
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
}
