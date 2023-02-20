package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

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
