package com.horizen.evm.params;

import com.horizen.evm.Address;
import com.horizen.evm.Hash;
import com.horizen.evm.results.EvmLog;

public class AddLogParams extends HandleParams {
    public final Address address;
    public final Hash[] topics;
    public final byte[] data;

    public AddLogParams(int handle, EvmLog evmLog) {
        super(handle);
        this.address = evmLog.address;
        this.topics = evmLog.topics;
        this.data = evmLog.data;
    }
}
