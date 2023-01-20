package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class StorageParams extends AccountParams {
    public Hash key;

    public StorageParams(int handle, Address address, byte[] key) {
        super(handle, address);
        this.address = address;
        this.key = Hash.fromBytes(key);
    }
}
