package com.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proof.SchnorrProof;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.secret.SchnorrSecret;
import com.horizen.serialization.Views;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
@JsonIgnoreProperties("valid")
public class SchnorrProposition implements ProofOfKnowledgeProposition<SchnorrSecret> {
    public static final int KEY_LENGTH = CryptoLibProvider.schnorrFunctions().schnorrPublicKeyLength();

    private final byte[] publicBytes;

    public SchnorrProposition(byte[] publicKey) {
        Objects.requireNonNull(publicKey, "Public key can't be null");

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
}
