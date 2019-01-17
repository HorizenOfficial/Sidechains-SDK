package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.horizen.box.Box;
import com.horizen.proposition.PublicKey25519Proposition;

import com.horizen.proof.Signature25519;
import scala.Tuple2;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;

import java.util.Arrays;


public class PrivateKey25519
        implements Secret<PrivateKey25519,
                          PublicKey25519Proposition,
                          Signature25519>
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

    @Override
    public boolean owns(Box<PublicKey25519Proposition> box) {
        if (box.proposition() != null &&
                java.util.Arrays.equals(_publicKeyBytes, box.proposition().pubKeyBytes()))
            return true;
        return false;
    }

    @Override
    public Signature25519 sign(byte[] message) {
        return new Signature25519(Curve25519.sign(_privateKeyBytes, message));
    }

    public static boolean verify(byte[] message, PublicKey25519Proposition publicImage,
                          Signature25519 proof) {
        return Curve25519.verify(proof.bytes(), message, publicImage.bytes());
    }

    public static Tuple2<PrivateKey25519, PublicKey25519Proposition> generateKeys(byte[] randomSeed) {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(randomSeed);
        PrivateKey25519 secret = new PrivateKey25519(keyPair._1, keyPair._2);
        return new Tuple2<PrivateKey25519, PublicKey25519Proposition>(secret, secret.publicImage());
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
