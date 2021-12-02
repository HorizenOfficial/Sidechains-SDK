package com.horizen.cryptolibprovider;

import com.horizen.box.Box;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.proposition.Proposition;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.certnative.NaiveThresholdSigProof;
import com.horizen.scutxonative.ScUtxoOutput;
import com.horizen.utils.WithdrawalEpochUtils;

public class CswCircuitImplZendoo implements CswCircuit {

    @Override
    public int utxoMerkleTreeHeight() {
        // TODO: choose proper merkle tree height compatible with the CSW circuit.
        return 12;
    }

    @Override
    public FieldElement getUtxoMerkleTreeLeaf(Box<Proposition> box) {
        ScUtxoOutput utxo = new ScUtxoOutput(box.proposition().bytes(), box.value(), box.nonce(), box.customFieldsHash());
        return utxo.getNullifier();
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
