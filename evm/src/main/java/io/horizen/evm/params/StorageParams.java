package io.horizen.evm.params;

import io.horizen.evm.Address;
import io.horizen.evm.Hash;

public class StorageParams extends AccountParams {
    public final Hash key;

    public StorageParams(int handle, Address address, Hash key) {
        super(handle, address);
        this.key = key;
    }
}
