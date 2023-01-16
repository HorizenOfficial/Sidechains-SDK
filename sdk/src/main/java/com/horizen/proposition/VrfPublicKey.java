package com.horizen.proposition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proof.VrfProof;
import com.horizen.secret.VrfSecretKey;
import com.horizen.serialization.Views;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.Arrays;
import java.util.Objects;

@JsonView(Views.Default.class)
@JsonIgnoreProperties("valid")
public class VrfPublicKey
        implements SingleSecretProofOfKnowledgeProposition<VrfSecretKey> {
    public final static int KEY_LENGTH = CryptoLibProvider.vrfFunctions().vrfPublicKeyLen();

    private final byte[] publicBytes;

    public VrfPublicKey(byte[] publicKey) {
        Objects.requireNonNull(publicKey, "Public key can't be null");

        if(publicKey.length != KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKey length, %d expected, %d found", KEY_LENGTH, publicKey.length));

        publicBytes = Arrays.copyOf(publicKey, publicKey.length);
    }

    public boolean verify(byte[] message, VrfProof proof) {
        return CryptoLibProvider.vrfFunctions().verifyProof(message, pubKeyBytes(), proof.bytes());
    }

    public boolean isValid() {
        return CryptoLibProvider.vrfFunctions().publicKeyIsValid(pubKeyBytes());
    }

    @JsonProperty("publicKey")
    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(publicBytes, publicBytes.length);
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

    @Override
    public String toString() {
        return "VrfPublicKey{" +
                "publicBytes=" + ByteUtils.toHexString(publicBytes) +
                '}';
    }
}
