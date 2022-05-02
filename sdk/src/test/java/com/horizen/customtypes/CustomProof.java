package com.horizen.customtypes;

import com.horizen.proof.Proof;
import com.horizen.proof.ProofSerializer;
import com.horizen.proposition.Proposition;

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
