package com.horizen.evm.interop;

import com.horizen.evm.utils.Hash;

public class GetLogsParams extends HandleParams {
    public final Hash txHash;

    public GetLogsParams(int handle, Hash txHash) {
        super(handle);
        this.txHash = txHash;
    }
}
