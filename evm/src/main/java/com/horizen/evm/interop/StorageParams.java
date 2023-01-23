package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class StorageParams extends AccountParams {
    public Hash key;

    public StorageParams(int handle, byte[] address, byte[] key) {
        super(handle, address);
        this.address = Address.fromBytes(address);
        this.key = Hash.fromBytes(key);
    }
}
