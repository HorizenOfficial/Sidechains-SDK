package com.horizen.cryptolibprovider;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;

public class CumulativeHashFunctions {
    public static int hashLength() {
        return PoseidonHash.HASH_LENGTH;
    }

    public static FieldElement computeCumulativeHash(FieldElement a, FieldElement b) {
        return PoseidonHash.computeHash(new FieldElement[] {a, b});
    }
}
