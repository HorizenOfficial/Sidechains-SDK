package com.horizen.evm.interop;

import com.horizen.evm.utils.Hash;

public class GetLogsParams extends HandleParams {
    public Hash txHash;

    public GetLogsParams(int handle, byte[] txHash) {
        super(handle);
        this.txHash = Hash.fromBytes(txHash);
    }
}
