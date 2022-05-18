package com.horizen.account.secret;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.proposition.PublicKeySecp256k1Proposition;
import com.horizen.account.utils.Secp256k1;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.secret.Secret;
import com.horizen.secret.SecretSerializer;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.util.Arrays;

import static com.horizen.account.secret.SecretsIdsEnum.PrivateKeySecp256k1SecretId;

public final class PrivateKeySecp256k1 implements Secret {
    private static final byte privateKey25519SecretId = PrivateKeySecp256k1SecretId.id();

    private final byte[] privateKey;

    public PrivateKeySecp256k1(byte[] privateKey) {
        if (privateKey.length != Secp256k1.PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException(String.format(
                "Incorrect private key length, %d expected, %d found",
                Secp256k1.PRIVATE_KEY_SIZE,
                privateKey.length
            ));
        }
        this.privateKey = Arrays.copyOf(privateKey, Secp256k1.PRIVATE_KEY_SIZE);
    }

    @Override
    public byte secretTypeId() {
        return privateKey25519SecretId;
    }

    @Override
    public SecretSerializer serializer() {
        return PrivateKeySecp256k1Serializer.getSerializer();
    }

    @Override
    public PublicKeySecp256k1Proposition publicImage() {
        var publicKey = ECKeyPair.create(privateKey).getPublicKey();
        return new PublicKeySecp256k1Proposition(Numeric.toBytesPadded(publicKey, Secp256k1.PUBLIC_KEY_SIZE));
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
        var pair = ECKeyPair.create(privateKey);
        return new SignatureSecp256k1(Sign.signMessage(message, pair, true));
    }

    public byte[] privateKeyBytes() {
        return Arrays.copyOf(privateKey, Secp256k1.PRIVATE_KEY_SIZE);
    }

    @Override
    public String toString() {
        return String.format("PrivateKeySecp256k1{privateKey=%s}", Numeric.toHexString(privateKey));
    }
}
