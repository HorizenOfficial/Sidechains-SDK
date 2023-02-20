package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class ProofParams extends AccountParams {
    public final Hash[] storageKeys;

    public ProofParams(int handle, Address address, Hash[] storageKeys) {
        super(handle, address);
        this.storageKeys = storageKeys;
    }
}


