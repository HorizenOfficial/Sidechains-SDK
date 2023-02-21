package com.horizen.evm.params;

import com.horizen.evm.Hash;

public class SetTxContextParams extends HandleParams {
    public final Hash txHash;
    public final Integer txIndex;

    public SetTxContextParams(int handle, Hash txHash, Integer txIndex) {
        super(handle);
        this.txHash = txHash;
        this.txIndex = txIndex;
    }
}
