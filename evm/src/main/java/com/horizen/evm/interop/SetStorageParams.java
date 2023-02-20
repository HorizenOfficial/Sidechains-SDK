package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class SetStorageParams extends StorageParams {
    public final Hash value;

    public SetStorageParams(int handle, Address address, Hash key, Hash value) {
        super(handle, address, key);
        this.value = value;
    }
}

