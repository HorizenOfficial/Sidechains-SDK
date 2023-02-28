package io.horizen.account.secret;

import io.horizen.account.proof.SignatureSecp256k1;
import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.utils.Secp256k1;
import io.horizen.proposition.ProofOfKnowledgeProposition;
import io.horizen.secret.Secret;
import io.horizen.secret.SecretSerializer;
import io.horizen.utils.BytesUtils;

import java.util.Arrays;

import static io.horizen.secret.SecretsIdsEnum.PrivateKeySecp256k1SecretId;

public final class PrivateKeySecp256k1 implements Secret {
    private static final byte privateKeySecp256k1SecretId = PrivateKeySecp256k1SecretId.id();

    private final byte[] privateKey;

    private final AddressProposition address;

    public PrivateKeySecp256k1(byte[] privateKey) {
        if (privateKey.length != Secp256k1.PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "Incorrect private key length, %d expected, %d found",
                    Secp256k1.PRIVATE_KEY_SIZE,
                    privateKey.length
            ));
        }
        this.privateKey = Arrays.copyOf(privateKey, Secp256k1.PRIVATE_KEY_SIZE);
        this.address = new AddressProposition(Secp256k1.getAddress(Secp256k1.getPublicKey(privateKey)));
    }

    @Override
    public byte secretTypeId() {
        return privateKeySecp256k1SecretId;
    }

    @Override
    public SecretSerializer serializer() {
        return PrivateKeySecp256k1Serializer.getSerializer();
    }

    @Override
    public AddressProposition publicImage() {
        return address;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof PrivateKeySecp256k1)) return false;
        if (obj == this) return true;
        var other = (PrivateKeySecp256k1) obj;
        return Arrays.equals(privateKey, other.privateKey);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(privateKey);
    }

    @Override
    public boolean owns(ProofOfKnowledgeProposition proposition) {
        return publicImage().equals(proposition);
    }

    @Override
    public SignatureSecp256k1 sign(byte[] message) {
        Secp256k1.Signature signature = Secp256k1.sign(privateKey, message);
        return new SignatureSecp256k1(signature.v, signature.r, signature.s);
    }

    public byte[] privateKeyBytes() {
        return Arrays.copyOf(privateKey, Secp256k1.PRIVATE_KEY_SIZE);
    }

    @Override
    public Boolean isPublicKeyValid() {
        // Considering that public key is not passed to this class, and it's generated from private key,
        // public key is always valid.
        return true;
    }

    @Override
    public String toString() {
        // Show only the first 4 bytes to protect the key
        return String.format("PrivateKeySecp256k1{privateKey=%s}", BytesUtils.toHexString(privateKey).substring(0, 8));
    }
}
