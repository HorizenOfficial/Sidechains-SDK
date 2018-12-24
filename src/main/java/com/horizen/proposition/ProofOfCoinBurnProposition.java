package com.horizen.proposition;

import com.horizen.ScorexEncoding;
import com.horizen.block.MainchainTrMerklePath;
import com.horizen.block.MainchainTransaction;

public final class ProofOfCoinBurnProposition extends ScorexEncoding implements Proposition
{
    MainchainTransaction _mainchainCoinBurnTransfer;
    MainchainTrMerklePath _merklePath;

    public ProofOfCoinBurnProposition(MainchainTransaction mainchainCoinBurnTransfer,
                                      MainchainTrMerklePath merklePath) {
        _mainchainCoinBurnTransfer = mainchainCoinBurnTransfer;
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
