package com.horizen.cryptolibprovider;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;

public class CumulativeHashFunctions {
    public static int hashLength() {
        return PoseidonHash.HASH_LENGTH;
    }

    public static byte[] computeCumulativeHash(byte[] a, byte[] b) {
        FieldElement fieldElementA = FieldElement.deserialize(a);
        FieldElement fieldElementB = FieldElement.deserialize(a);
        FieldElement fieldElementHash = PoseidonHash.computeHash(new FieldElement[] {fieldElementA, fieldElementB});
        byte[] hash = fieldElementHash.serializeFieldElement();

        fieldElementA.freeFieldElement();
        fieldElementB.freeFieldElement();
        fieldElementHash.freeFieldElement();

        return hash;
    }
}
