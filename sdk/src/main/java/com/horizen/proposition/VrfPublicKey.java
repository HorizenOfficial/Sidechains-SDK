package com.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.secret.VrfSecretKey;
import com.horizen.serialization.Views;
import com.horizen.vrf.VrfLoader;
import com.horizen.proof.VrfProof;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
@JsonIgnoreProperties("valid")
public class VrfPublicKey implements ProofOfKnowledgeProposition<VrfSecretKey> {
    private final byte[] publicBytes;

    public VrfPublicKey(byte[] publicKey) {
        Objects.requireNonNull(publicKey, "Public key can't be null");

        publicBytes = Arrays.copyOf(publicKey, publicKey.length);
    }

    public boolean verify(byte[] message, VrfProof proof) {
        return VrfLoader.vrfFunctions().verifyMessage(message, pubKeyBytes(), proof.bytes());
    }

    //Do we need that function at all?
    public boolean isValid() {
        return VrfLoader.vrfFunctions().publicKeyIsValid(pubKeyBytes());
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
        return VrfPublicKeySerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VrfPublicKey that = (VrfPublicKey) o;
        return Arrays.equals(publicBytes, that.publicBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(publicBytes);
    }

    public static VrfPublicKey parseBytes(byte[] bytes) {
        return new VrfPublicKey(bytes);
    }

    @Override
    public String toString() {
        return "VrfPublicKey{" +
                "publicBytes=" + Arrays.toString(publicBytes) +
                '}';
    }
}
