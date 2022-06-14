package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class StorageParams extends AccountParams {
    public Hash key;

    public StorageParams() {
    }

    public StorageParams(int handle, byte[] address, byte[] key) {
        super(handle, address);
        this.address = Address.FromBytes(address);
        this.key = Hash.FromBytes(key);
    }
}
