package com.horizen.proposition;

import com.horizen.ScorexEncoding;
import com.horizen.block.MainchainTrMerklePath;
import com.horizen.block.MainchainTransaction;

public final class ProofOfBeingIncludedIntoCertificateProposition extends ScorexEncoding implements Proposition
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