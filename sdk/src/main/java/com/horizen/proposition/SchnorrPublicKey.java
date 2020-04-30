package com.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proof.SchnorrSignature;
import com.horizen.backwardtransfer.BackwardTransferLoader;
import com.horizen.secret.SchnorrSecretKey;
import com.horizen.serialization.Views;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
@JsonIgnoreProperties("valid")
public class SchnorrPublicKey implements ProofOfKnowledgeProposition<SchnorrSecretKey> {
    private final byte[] publicBytes;

    public SchnorrPublicKey(byte[] publicKey) {
        Objects.requireNonNull(publicKey, "Public key can't be null");

        publicBytes = Arrays.copyOf(publicKey, publicKey.length);
    }

    public boolean verify(byte[] message, SchnorrSignature signature) {
        return BackwardTransferLoader.schnorrFunctions().verify(message, pubKeyBytes(), signature.bytes());
    }


    @JsonProperty("publicKey")
    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(publicBytes, publicBytes.length);
    }

    @Override
    public byte[] bytes() {
        return pubKeyBytes();
    }

    @Override
    public PropositionSerializer serializer() {
        return SchnorrPublicKeySerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchnorrPublicKey that = (SchnorrPublicKey) o;
        return Arrays.equals(publicBytes, that.publicBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(publicBytes);
    }

    public static SchnorrPublicKey parseBytes(byte[] bytes) {
        return new SchnorrPublicKey(bytes);
    }

    @Override
    public String toString() {
        return "SchnorrPublicKey{" +
                "publicBytes=" + Arrays.toString(publicBytes) +
                '}';
    }
}
