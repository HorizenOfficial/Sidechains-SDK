package com.horizen.proof;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.VrfPublicKey;
import com.horizen.secret.VrfSecretKey;
import com.horizen.serialization.Views;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import com.horizen.vrf.VrfOutput;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.horizen.proof.CoreProofsIdsEnum.VrfProofId;

@JsonView(Views.Default.class)
@JsonIgnoreProperties("typeId")
public final class VrfProof implements ProofOfKnowledge<VrfSecretKey, VrfPublicKey> {
    private final byte[] proofBytes;

    public VrfProof(byte[] proof) {
        Objects.requireNonNull(proof, "Vrf proof can't be null");

        proofBytes = Arrays.copyOf(proof, proof.length);
    }

    public Optional<VrfOutput> proofToVrfOutput(VrfPublicKey publicKey, byte[] message) {
        return CryptoLibProvider.vrfFunctions().proofToOutput(publicKey.pubKeyBytes(), message, proofBytes).map(VrfOutput::new);
    }

    @Override
    public boolean isValid(VrfPublicKey proposition, byte[] message) {
        return CryptoLibProvider.vrfFunctions().verifyProof(message, proposition.pubKeyBytes(), proofBytes);
    }

    @JsonProperty("vrfProof")
    @Override
    public byte[] bytes() {
        return Arrays.copyOf(proofBytes, proofBytes.length);
    }

    @Override
    public ProofSerializer serializer() {
        return VrfProofSerializer.getSerializer();
    }

    @Override
    public byte proofTypeId() {
        return VrfProofId.id();
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

    public static VrfProof parse(byte[] bytes) {
        return new VrfProof(bytes);
    }

    @Override
    public String toString() {
        return "VrfProof{" +
                "proofBytes=" + ByteUtils.toHexString(proofBytes) +
                '}';
    }
}
