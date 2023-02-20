package io.horizen.evm.params;

import io.horizen.evm.Hash;

public class GetLogsParams extends HandleParams {
    public final Hash txHash;

    public GetLogsParams(int handle, Hash txHash) {
        super(handle);
        this.txHash = txHash;
    }
}
