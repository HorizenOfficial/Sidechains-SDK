package com.horizen.evm.params;

import com.horizen.evm.Hash;

public class GetLogsParams extends HandleParams {
    public final Hash txHash;

    public GetLogsParams(int handle, Hash txHash) {
        super(handle);
        this.txHash = txHash;
    }
}
