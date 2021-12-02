package com.horizen.cryptolibprovider;

import com.horizen.box.Box;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.proposition.Proposition;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.certnative.NaiveThresholdSigProof;
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

    private long rangeSize(int withdrawalEpochLength) {
        int submissionWindowLength = WithdrawalEpochUtils.certificateSubmissionWindowLength(withdrawalEpochLength);
        return 2L * withdrawalEpochLength + submissionWindowLength;
    }

    public boolean generateCoboundaryMarlinKeys(int withdrawalEpochLength, String provingKeyPath, String verificationKeyPath) {
        long rangeSize = rangeSize(withdrawalEpochLength); // TODO Use it in future implementation of CSW circuit

        // TODO Replace with corresponding method for CSW keypair.
        return NaiveThresholdSigProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, 1, 2, provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }
}
