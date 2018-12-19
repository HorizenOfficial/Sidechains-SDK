package com.horizen.proof;

import com.horizen.proposition.ProofOfCoinBurnProposition;

final class ProofOfCoinBurn implements Proof<ProofOfCoinBurnProposition>
{
    @Override
    public boolean isValid(ProofOfCoinBurnProposition proposition, byte[] merkleRoot) {
        // Get hash of proposition._mainchainCoinBurnTransfer
        // calculate the merkle root for hash and proposition._merklePath
        // compare calculated root with provided
        return false;
    }

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public ProofSerializer serializer() {
        return null;
    }
}
