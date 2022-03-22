package com.horizen.secret;

import com.horizen.proof.Signature25519;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Ed25519;
import java.util.Arrays;

import static com.horizen.secret.SecretsIdsEnum.PrivateKey25519SecretId;


public final class PrivateKey25519 implements Secret
{
    public static final int PRIVATE_KEY_LENGTH = Ed25519.privateKeyLength();
    public static final int PUBLIC_KEY_LENGTH = Ed25519.publicKeyLength();
    private static final byte privateKey25519SecretId = PrivateKey25519SecretId.id();

    final byte[] privateKeyBytes;
    final byte[] publicKeyBytes;

    public PrivateKey25519(byte[] privateKeyBytes, byte[] publicKeyBytes)
    {
        if(privateKeyBytes.length != PRIVATE_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect private key length, %d expected, %d found", PRIVATE_KEY_LENGTH,
                    privateKeyBytes.length));
        if(publicKeyBytes.length != PUBLIC_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", PUBLIC_KEY_LENGTH,
                    publicKeyBytes.length));

        this.privateKeyBytes = Arrays.copyOf(privateKeyBytes, PRIVATE_KEY_LENGTH);
        this.publicKeyBytes = Arrays.copyOf(publicKeyBytes, PUBLIC_KEY_LENGTH);
    }

    @Override
    public byte secretTypeId() {
        return privateKey25519SecretId;
    }

    @Override
    public SecretSerializer serializer() {
        return PrivateKey25519Serializer.getSerializer();
    }

    @Override
    public PublicKey25519Proposition publicImage() {
        return new PublicKey25519Proposition(publicKeyBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrivateKey25519 that = (PrivateKey25519) o;
        return Arrays.equals(privateKeyBytes, that.privateKeyBytes) &&
                Arrays.equals(publicKeyBytes, that.publicKeyBytes);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(privateKeyBytes);
        result = 31 * result + Arrays.hashCode(publicKeyBytes);
        return result;
    }

    @Override
    public boolean owns(ProofOfKnowledgeProposition proposition) {
        return publicImage().equals(proposition);
    }

    @Override
    public Signature25519 sign(byte[] message) {
        return new Signature25519(Ed25519.sign(privateKeyBytes, message, publicKeyBytes));
    }

    public byte[] privateKey() {
        return Arrays.copyOf(privateKeyBytes, PRIVATE_KEY_LENGTH);
    }

    @Override
    public String toString() {
        return "PrivateKey25519{" +
                "privateKeyBytes=" + BytesUtils.toHexString(privateKeyBytes) +
                ", publicKeyBytes=" + BytesUtils.toHexString(publicKeyBytes) +
                '}';
    }

    @Override
    public Boolean isCustom() { return false; }
}
