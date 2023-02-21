package com.horizen.evm.params;

import com.horizen.evm.Address;
import com.horizen.evm.Hash;

public class ProofParams extends AccountParams {
    public final Hash[] storageKeys;

    public ProofParams(int handle, Address address, Hash[] storageKeys) {
        super(handle, address);
        this.storageKeys = storageKeys;
    }
}


