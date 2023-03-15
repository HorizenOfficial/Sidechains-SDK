package io.horizen.customtypes;

import io.horizen.proof.Proof;
import io.horizen.proof.ProofSerializer;
import io.horizen.proposition.Proposition;

public final class CustomProof implements Proof<Proposition> {

    int number;

    public CustomProof(int number) {
        this.number = number;
    }

    @Override
    public boolean isValid(Proposition proposition, byte[] message) {
        return true;
    }

    @Override
    public ProofSerializer serializer() {
        return CustomProofSerializer.getSerializer();
    }
}
