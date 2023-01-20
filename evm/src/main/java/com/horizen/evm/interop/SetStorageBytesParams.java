package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

public class SetStorageBytesParams extends StorageParams {
    public byte[] value;

    public SetStorageBytesParams(int handle, Address address, byte[] key, byte[] value) {
        super(handle, address, key);
        this.value = value;
    }
}
