package io.horizen.evm.params;

import io.horizen.evm.Address;
import io.horizen.evm.Hash;

public class SetStorageParams extends StorageParams {
    public final Hash value;

    public SetStorageParams(int handle, Address address, Hash key, Hash value) {
        super(handle, address, key);
        this.value = value;
    }
}

