package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class StorageParams extends AccountParams {
    public final Hash key;

    public StorageParams(int handle, Address address, Hash key) {
        super(handle, address);
        this.key = key;
    }
}
