package io.horizen.evm.params;

import io.horizen.evm.Hash;

public class SetTxContextParams extends HandleParams {
    public final Hash txHash;
    public final Integer txIndex;

    public SetTxContextParams(int handle, Hash txHash, Integer txIndex) {
        super(handle);
        this.txHash = txHash;
        this.txIndex = txIndex;
    }
}
