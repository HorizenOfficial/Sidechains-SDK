package com.horizen.evm.interop;

import com.horizen.evm.utils.Hash;

public class SetTxContextParams extends HandleParams {
    public Hash txHash;
    public Integer txIndex;

    public SetTxContextParams(int handle, Hash txHash, Integer txIndex) {
        super(handle);
        this.txHash = txHash;
        this.txIndex = txIndex;
    }
}
