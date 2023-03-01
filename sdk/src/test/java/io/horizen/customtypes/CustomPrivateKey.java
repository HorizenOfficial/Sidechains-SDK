package io.horizen.customtypes;

import io.horizen.proof.ProofOfKnowledge;
import io.horizen.proposition.ProofOfKnowledgeProposition;
import io.horizen.secret.Secret;
import io.horizen.secret.SecretSerializer;
import io.horizen.utils.BytesUtils;

import java.util.Arrays;

public class CustomPrivateKey implements Secret
{
    public static final int PRIVATE_KEY_LENGTH = 128;
    public static final int PUBLIC_KEY_LENGTH = 128;
    public static final byte SECRET_TYPE_ID = 1;

    byte[] privateKeyBytes;
    byte[] publicKeyBytes;

    public CustomPrivateKey(byte[] privateKeyBytes, byte[] publicKeyBytes)
    {
        if(privateKeyBytes.length != PRIVATE_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", PRIVATE_KEY_LENGTH,
                    privateKeyBytes.length));
        if(publicKeyBytes.length != PUBLIC_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", PUBLIC_KEY_LENGTH,
                    publicKeyBytes.length));

        this.privateKeyBytes = Arrays.copyOf(privateKeyBytes, PRIVATE_KEY_LENGTH);
        this.publicKeyBytes = Arrays.copyOf(publicKeyBytes, PUBLIC_KEY_LENGTH);
    }

    @Override
    public byte secretTypeId() {
        return SECRET_TYPE_ID;
    }

    @Override
    public CustomPublicKeyProposition publicImage() {
        return new CustomPublicKeyProposition(publicKeyBytes);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomPrivateKey that = (CustomPrivateKey) o;
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
    public String toString() {
        // Show only the first 4 bytes to protect the key
        return String.format("CustomPrivateKey{privateKey=%s}", BytesUtils.toHexString(privateKeyBytes).substring(0, 8));
    }

    @Override
    public Boolean isPublicKeyValid() {
        return true;
    }
}
