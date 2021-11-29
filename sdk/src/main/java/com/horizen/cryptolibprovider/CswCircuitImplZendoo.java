package com.horizen.cryptolibprovider;

import com.horizen.box.Box;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.proposition.Proposition;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.sigproofnative.NaiveThresholdSigProof;
import com.horizen.utils.WithdrawalEpochUtils;

import java.util.Arrays;

public class CswCircuitImplZendoo implements CswCircuit {

    @Override
    public int utxoMerkleTreeHeight() {
        // TODO: choose proper merkle tree height compatible with the CSW circuit.
        return 12;
    }

    @Override
    public FieldElement getUtxoMerkleTreeLeaf(Box<Proposition> box) {
        // TODO: use method from sc-cryptolib
        return FieldElement.createFromLong(Arrays.hashCode(box.bytes()));
    }

    private static final int maxProofSize = 9 * 1024;
    private static final int maxVkSize = 9 * 1024;

    public boolean generateCoboundaryMarlinKeys(int withdrawalEpochLen, String provingKeyPath, String verificationKeyPath) {
        long withdrawalWindowLen = WithdrawalEpochUtils.certificateSubmissionWindowLength(withdrawalEpochLen);
        long rangeSize = 2 * withdrawalEpochLen + withdrawalWindowLen; // TODO Use it in future implementation of CSW circuit

        // TODO Replace with corresponding method for CSW keypair.
        return NaiveThresholdSigProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, 1, provingKeyPath, verificationKeyPath, maxProofSize, maxVkSize);
    }
}
