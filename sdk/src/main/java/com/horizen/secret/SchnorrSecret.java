package com.horizen.secret;

import com.horizen.proof.SchnorrProof;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.SchnorrProposition;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;
import java.util.Objects;

import static com.horizen.secret.SecretsIdsEnum.SchnorrSecretKeyId;

public class SchnorrSecret implements Secret {
    public static final int SECRET_KEY_LENGTH = CryptoLibProvider.schnorrFunctions().schnorrSecretKeyLength();
    public static final int PUBLIC_KEY_LENGTH = CryptoLibProvider.schnorrFunctions().schnorrPublicKeyLength();

    final byte[] secretBytes;
    final byte[] publicBytes;

    public SchnorrSecret(byte[] secretKey, byte[] publicKey) {
        Objects.requireNonNull(secretKey, "Secret key can't be null");
        Objects.requireNonNull(publicKey, "Public key can't be null");

        if(secretKey.length != SECRET_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect secret key length, %d expected, %d found", SECRET_KEY_LENGTH,
                    secretKey.length));
        if(publicKey.length != PUBLIC_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect public key length, %d expected, %d found", PUBLIC_KEY_LENGTH,
                    publicKey.length));

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
    public SchnorrProposition publicImage() {
        byte[] publicKey = Arrays.copyOf(publicBytes, publicBytes.length);
        return new SchnorrProposition(publicKey);
    }

    @Override
    public SecretSerializer serializer() {
        return SchnorrSecretSerializer.getSerializer();
    }

    @Override
    public boolean owns(ProofOfKnowledgeProposition proposition) {
        return Arrays.equals(publicBytes, proposition.pubKeyBytes());
    }

    @Override
    public SchnorrProof sign(byte[] message) {
        return new SchnorrProof(CryptoLibProvider.schnorrFunctions().sign(getSecretBytes(), getPublicBytes(), message));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchnorrSecret that = (SchnorrSecret) o;
        return Arrays.equals(secretBytes, that.secretBytes) &&
                Arrays.equals(publicBytes, that.publicBytes);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(secretBytes);
        result = 31 * result + Arrays.hashCode(publicBytes);
        return result;
    }

    @Override
    public Boolean isCustom() { return false; }

    @Override
    public String toString() {
        // Show only the first 4 bytes to protect the key
        return String.format("SchnorrSecret{privateKey=%s}", BytesUtils.toHexString(secretBytes).substring(0, 8));
    }

    @Override
    public Boolean isPublicKeyValid() {
        byte[] correctPublicKey = CryptoLibProvider.schnorrFunctions().getPublicKey(secretBytes);
        return Arrays.equals(publicBytes, correctPublicKey);
    }
}