package com.horizen.cryptolibprovider;

import com.horizen.box.Box;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.proposition.Proposition;

public interface CswCircuit {
    int utxoMerkleTreeHeight();

    FieldElement getUtxoMerkleTreeLeaf(Box<Proposition> box);

    boolean generateCoboundaryMarlinKeys(int withdrawalEpochLen, String provingKeyPath, String verificationKeyPath);
}
