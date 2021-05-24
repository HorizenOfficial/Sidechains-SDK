package com.horizen.cryptolibprovider;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.utils.BytesUtils;

public class CumulativeHashFunctions {
    public static int hashLength() {
        return PoseidonHash.HASH_LENGTH;
    }

    public static byte[] computeCumulativeHash(byte[] prevCumulativeHash, byte[] hashScTxsCommitment) {
        FieldElement fieldElementA = null;
        FieldElement fieldElementB = null;
        FieldElement fieldElementHash = null;
        PoseidonHash digest = null;
        try {
            byte[] prevCumulativeHashLE = BytesUtils.reverseBytes(prevCumulativeHash);
            byte[] hashScTxsCommitmentLE = BytesUtils.reverseBytes(hashScTxsCommitment);
            fieldElementA = FieldElement.deserialize(prevCumulativeHashLE);
            fieldElementB = FieldElement.deserialize(hashScTxsCommitmentLE);
            digest = PoseidonHash.getInstanceConstantLength(2);
            digest.update(fieldElementA);
            digest.update(fieldElementB);
            fieldElementHash = digest.finalizeHash();
            byte[] hashLE = fieldElementHash.serializeFieldElement();
            return BytesUtils.reverseBytes(hashLE);
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
