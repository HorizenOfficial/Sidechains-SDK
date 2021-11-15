package com.horizen.cryptolibprovider;

import com.horizen.box.Box;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.proposition.Proposition;

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
}
