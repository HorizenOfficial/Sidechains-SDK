package io.horizen.evm.params;

import io.horizen.evm.Address;
import io.horizen.evm.Hash;

public class ProofParams extends AccountParams {
    public final Hash[] storageKeys;

    public ProofParams(int handle, Address address, Hash[] storageKeys) {
        super(handle, address);
        this.storageKeys = storageKeys;
    }
}


