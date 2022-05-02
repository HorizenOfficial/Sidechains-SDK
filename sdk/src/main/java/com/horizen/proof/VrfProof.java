package com.horizen.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.VrfPublicKey;
import com.horizen.secret.VrfSecretKey;
import com.horizen.serialization.Views;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.vrf.VrfOutput;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.Arrays;
import java.util.Optional;

@JsonView(Views.Default.class)
public final class VrfProof implements ProofOfKnowledge<VrfSecretKey, VrfPublicKey> {
    public static final int PROOF_LENGTH = CryptoLibProvider.vrfFunctions().vrfProofLen();

    @JsonProperty("vrfProof")
    final byte[] proofBytes;

    public VrfProof(byte[] proof) {
        if (proof.length != PROOF_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect proof length, %d expected, %d found", PROOF_LENGTH,
                    proof.length));

        proofBytes = Arrays.copyOf(proof, proof.length);
    }

    public Optional<VrfOutput> proofToVrfOutput(VrfPublicKey publicKey, byte[] message) {
        return CryptoLibProvider.vrfFunctions().proofToOutput(publicKey.pubKeyBytes(), message, proofBytes).map(VrfOutput::new);
    }

    @Override
    public boolean isValid(VrfPublicKey proposition, byte[] message) {
        return CryptoLibProvider.vrfFunctions().verifyProof(message, proposition.pubKeyBytes(), proofBytes);
    }

    @Override
    public ProofSerializer serializer() {
        return VrfProofSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        com.horizen.proof.VrfProof vrfProof = (com.horizen.proof.VrfProof) o;
        return Arrays.equals(proofBytes, vrfProof.proofBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(proofBytes);
    }

    @Override
    public String toString() {
        return "VrfProof{" +
                "proofBytes=" + ByteUtils.toHexString(proofBytes) +
                '}';
    }
}
