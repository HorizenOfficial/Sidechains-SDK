package com.horizen.evm.params;

import com.horizen.evm.Address;
import com.horizen.evm.Hash;

public class StorageParams extends AccountParams {
    public final Hash key;

    public StorageParams(int handle, Address address, Hash key) {
        super(handle, address);
        this.key = key;
    }
}
