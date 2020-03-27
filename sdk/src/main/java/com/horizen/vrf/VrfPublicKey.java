package com.horizen.vrf;

import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PropositionSerializer;

import java.util.Arrays;
import java.util.Objects;

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
}
