package io.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.proof.SchnorrProof;
import io.horizen.cryptolibprovider.CryptoLibProvider;
import io.horizen.secret.SchnorrSecret;
import io.horizen.json.Views;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
@JsonIgnoreProperties("valid")
public class SchnorrProposition
        implements SingleSecretProofOfKnowledgeProposition<SchnorrSecret> {
    public static final int KEY_LENGTH = CryptoLibProvider.schnorrFunctions().schnorrPublicKeyLength();

    private final byte[] publicBytes;

    public SchnorrProposition(byte[] publicKey) {
        this(publicKey, false);
    }

    public SchnorrProposition(byte[] publicKey, boolean checkPublicKey) {
        Objects.requireNonNull(publicKey, "Public key can't be null");

        if (publicKey.length != KEY_LENGTH) {
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", KEY_LENGTH, publicKey.length));
        }

        if (checkPublicKey && !CryptoLibProvider.schnorrFunctions().publicKeyIsValid(publicKey)) {
            throw new IllegalArgumentException("Public key is not valid.");
        }

        publicBytes = Arrays.copyOf(publicKey, publicKey.length);
    }

    public boolean verify(byte[] message, SchnorrProof signature) {
        return CryptoLibProvider.schnorrFunctions().verify(message, pubKeyBytes(), signature.bytes());
    }

    @JsonProperty("publicKey")
    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(publicBytes, publicBytes.length);
    }

    @Override
    public PropositionSerializer serializer() {
        return SchnorrPropositionSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchnorrProposition that = (SchnorrProposition) o;
        return Arrays.equals(publicBytes, that.publicBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(publicBytes);
    }

    @Override
    public String toString() {
        return "SchnorrPublicKey{" +
                "publicBytes=" + Arrays.toString(publicBytes) +
                '}';
    }

    public boolean isValid() {
        return CryptoLibProvider.schnorrFunctions().publicKeyIsValid(pubKeyBytes());
    }
}
