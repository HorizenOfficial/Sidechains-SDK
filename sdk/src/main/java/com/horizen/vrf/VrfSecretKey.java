package com.horizen.vrf;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretSerializer;
import com.horizen.secret.SecretsIdsEnum;

import java.util.Arrays;
import java.util.Objects;

public class VrfSecretKey implements Secret {
    private final byte[] secretBytes;
    private final byte[] publicBytes;

    public VrfSecretKey(byte[] secretKey, byte[] publicKey) {
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

    public VrfProof prove(byte[] message) {
        return new VrfProof(VrfLoader.vrfFunctions().createVrfProofForMessage(getSecretBytes(), getPublicBytes(), message));
    }

    @Override
    public byte secretTypeId() {
        return SecretsIdsEnum.VrfPrivateKey.id();
    }

    @Override
    public VrfPublicKey publicImage() {
        byte[] publicKey = Arrays.copyOf(publicBytes, publicBytes.length);
        return new VrfPublicKey(publicKey);
    }

    @Override
    public byte[] bytes() {
        int secretLength = secretBytes.length;
        return Bytes.concat(Ints.toByteArray(secretLength), secretBytes, publicBytes);
    }

    public static VrfSecretKey parse(byte[] bytes) {
        int secretKeyOffset = Ints.BYTES;
        int secretKeyLength = Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, secretKeyOffset));
        int publicKeyOffset = secretKeyOffset + secretKeyLength;

        byte[] secretKey = Arrays.copyOfRange(bytes, secretKeyOffset, publicKeyOffset);
        byte[] publicKey = Arrays.copyOfRange(bytes, publicKeyOffset, bytes.length);

        return new VrfSecretKey(secretKey, publicKey);
    }

    @Override
    public SecretSerializer serializer() {
        return null;
    }

    @Override
    public boolean owns(ProofOfKnowledgeProposition proposition) {
        return Arrays.equals(publicBytes, proposition.pubKeyBytes());
    }

    @Override
    public ProofOfKnowledge sign(byte[] message) {
        return prove(message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VrfSecretKey that = (VrfSecretKey) o;
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
