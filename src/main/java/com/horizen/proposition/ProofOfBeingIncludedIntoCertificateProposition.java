package com.horizen.proposition;

import com.horizen.ScorexEncodingImpl;
import com.horizen.block.MainchainTrMerklePath;
import com.horizen.block.MainchainTransaction;

public final class ProofOfBeingIncludedIntoCertificateProposition extends ScorexEncodingImpl implements Proposition
{
    MainchainTransaction _mainchainCertifierLockTransfer;
    MainchainTrMerklePath _merklePath;

    public ProofOfBeingIncludedIntoCertificateProposition(MainchainTransaction mainchainCertifierLockTransfer,
                                                          MainchainTrMerklePath merklePath) {
        _mainchainCertifierLockTransfer = mainchainCertifierLockTransfer;
        _merklePath = merklePath;
    }


    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public PropositionSerializer serializer() {
        return null;
    }
}