package com.horizen.customtypes;

import com.google.common.primitives.Ints;
import com.horizen.proof.Proof;
import com.horizen.proof.ProofSerializer;
import com.horizen.proposition.Proposition;

public final class CustomProof implements Proof<Proposition> {

    public static byte PROOF_TYPE_ID = 10;

    private int number;

    public CustomProof(int number) {
        this.number = number;
    }

    @Override
    public boolean isValid(Proposition proposition, byte[] message) {
        return true;
    }

    @Override
    public byte[] bytes() {
        return Ints.toByteArray(number);
    }

    public static CustomProof parseBytes(byte[] bytes) {
        return new CustomProof(Ints.fromByteArray(bytes));
    }

    @Override
    public ProofSerializer serializer() {
        return CustomProofSerializer.getSerializer();
    }

    @Override
    public byte proofTypeId() {
        return 10;
    }
}
