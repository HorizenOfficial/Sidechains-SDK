package com.horizen.proof;

import com.horizen.proposition.ProofOfBeingIncludedIntoCertificateProposition;

final class ProofOfBeingIncludedIntoCertificate implements Proof<ProofOfBeingIncludedIntoCertificateProposition>
{
    @Override
    public boolean isValid(ProofOfBeingIncludedIntoCertificateProposition proposition, byte[] merkleRoot) {
        // Get hash of proposition._mainchainCertifierLockTransfer
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
