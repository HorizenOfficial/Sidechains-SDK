package com.horizen.cryptolibprovider;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.librustsidechains.Constants;

public class CumulativeHashFunctions {
    public static int hashLength() {
        return Constants.FIELD_ELEMENT_LENGTH();
    }

    // Note: both arguments should be a LE form of the FieldElements expected by sc-cryptolib.
    // resulting hash is returned as is in LE.
    public static byte[] computeCumulativeHash(byte[] prevCumulativeHash, byte[] hashScTxsCommitment) {
        FieldElement fieldElementA = null;
        FieldElement fieldElementB = null;
        FieldElement fieldElementHash = null;
        PoseidonHash digest = null;
        try {
            fieldElementA = FieldElement.deserialize(prevCumulativeHash);
            fieldElementB = FieldElement.deserialize(hashScTxsCommitment);
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
