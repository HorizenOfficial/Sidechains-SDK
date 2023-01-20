package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class SetStorageParams extends StorageParams {
    public Hash value;

    public SetStorageParams(int handle, Address address, byte[] key, byte[] value) {
        super(handle, address, key);
        this.value = Hash.fromBytes(value);
    }
}

