package com.horizen.cryptolibprovider;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;

public class CumulativeHashFunctions {
    public static int hashLength() {
        return PoseidonHash.HASH_LENGTH;
    }

    public static byte[] computeCumulativeHash(byte[] a, byte[] b) {
        FieldElement fieldElementA = null;
        FieldElement fieldElementB = null;
        FieldElement fieldElementHash = null;
        PoseidonHash digest = null;
        try {
            fieldElementA = FieldElement.deserialize(a);
            fieldElementB = FieldElement.deserialize(b);
            digest = PoseidonHash.getInstanceConstantLength(2);
            digest.update(fieldElementA);
            digest.update(fieldElementB);
            fieldElementHash = digest.finalizeHash();
            return fieldElementHash.serializeFieldElement();
        } catch (Exception ex) {
            throw new IllegalStateException("Error on computing Cumulative Commitment Tree Hash:" + ex.getMessage(), ex);
        } finally {
            if (fieldElementA != null)
                fieldElementA.freeFieldElement();
            if (fieldElementB != null)
                fieldElementB.freeFieldElement();
            if (fieldElementHash != null)
                fieldElementHash.freeFieldElement();
            if(digest != null)
                digest.freePoseidonHash();
        }
    }
}
