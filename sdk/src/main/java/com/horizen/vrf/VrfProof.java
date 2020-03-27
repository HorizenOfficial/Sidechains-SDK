package com.horizen.vrf;

import com.horizen.proof.ProofOfKnowledge;
import com.horizen.proof.ProofSerializer;

import java.util.Arrays;
import java.util.Objects;

import static com.horizen.proof.CoreProofsIdsEnum.VrfProof;

public class VrfProof implements ProofOfKnowledge<VrfSecretKey, VrfPublicKey> {
    private final byte[] proofBytes;

    public VrfProof(byte[] proof) {
        Objects.requireNonNull(proof, "Public key can't be null");

        proofBytes = Arrays.copyOf(proof, proof.length);
    }

    public byte[] proofToVRFHash(VrfPublicKey publicKey, byte[] message) {
        return VrfLoader.vrfFunctions().vrfProofToVrfHash(publicKey.pubKeyBytes(), message, proofBytes);
    }

    @Override
    public boolean isValid(VrfPublicKey proposition, byte[] message) {
        return VrfLoader.vrfFunctions().verifyMessage(message, proposition.pubKeyBytes(), proofBytes);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(proofBytes, proofBytes.length);
    }

    @Override
    public ProofSerializer serializer() {
        return null;
    }

    @Override
    public byte proofTypeId() {
        return VrfProof.id();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        com.horizen.vrf.VrfProof vrfProof = (com.horizen.vrf.VrfProof) o;
        return Arrays.equals(proofBytes, vrfProof.proofBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(proofBytes);
    }

    public static VrfProof parse(byte[] bytes) {
        return new VrfProof(bytes);
    }
}
