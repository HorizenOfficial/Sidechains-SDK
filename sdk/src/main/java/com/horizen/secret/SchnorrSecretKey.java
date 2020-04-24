package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.proof.SchnorrSignature;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.SchnorrPublicKey;
import com.horizen.backwardtransfer.BackwardTransferLoader;

import java.util.Arrays;
import java.util.Objects;

import static com.horizen.secret.SecretsIdsEnum.SchnorrSecretKeyId;

public class SchnorrSecretKey implements Secret {
    private final byte[] secretBytes;
    private final byte[] publicBytes;

    public SchnorrSecretKey(byte[] secretKey, byte[] publicKey) {
        Objects.requireNonNull(secretKey, "Secret key can't be null");
        Objects.requireNonNull(publicKey, "Public key can't be null");

        secretBytes =  Arrays.copyOf(secretKey, secretKey.length);
        publicBytes = Arrays.copyOf(publicKey, publicKey.length);
    }

    private byte[] getSecretBytes() {
        return Arrays.copyOf(secretBytes, secretBytes.length);
    }

    public byte[] getPublicBytes() {
        return publicBytes;
    }

    @Override
    public byte secretTypeId() {
        return SchnorrSecretKeyId.id();
    }

    @Override
    public SchnorrPublicKey publicImage() {
        byte[] publicKey = Arrays.copyOf(publicBytes, publicBytes.length);
        return new SchnorrPublicKey(publicKey);
    }

    @Override
    public byte[] bytes() {
        int secretLength = secretBytes.length;
        return Bytes.concat(Ints.toByteArray(secretLength), secretBytes, publicBytes);
    }

    public static SchnorrSecretKey parse(byte[] bytes) {
        int secretKeyOffset = Ints.BYTES;
        int secretKeyLength = Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, secretKeyOffset));
        int publicKeyOffset = secretKeyOffset + secretKeyLength;

        byte[] secretKey = Arrays.copyOfRange(bytes, secretKeyOffset, publicKeyOffset);
        byte[] publicKey = Arrays.copyOfRange(bytes, publicKeyOffset, bytes.length);

        return new SchnorrSecretKey(secretKey, publicKey);
    }

    @Override
    public SecretSerializer serializer() {
        return SchnorrSecretKeySerializer.getSerializer();
    }

    @Override
    public boolean owns(ProofOfKnowledgeProposition proposition) {
        return Arrays.equals(publicBytes, proposition.pubKeyBytes());
    }

    @Override
    public SchnorrSignature sign(byte[] message) {
        return new SchnorrSignature(BackwardTransferLoader.schnorrFunctions().sign(getSecretBytes(), getPublicBytes(), message));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchnorrSecretKey that = (SchnorrSecretKey) o;
        return Arrays.equals(secretBytes, that.secretBytes) &&
                Arrays.equals(publicBytes, that.publicBytes);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(secretBytes);
        result = 31 * result + Arrays.hashCode(publicBytes);
        return result;
    }
}