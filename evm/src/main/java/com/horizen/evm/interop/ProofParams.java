package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

public class ProofParams extends AccountParams {
    public Hash[] keys;

    public ProofParams(int handle, Address address, Hash[] keys) {
        super(handle, address);
        this.address = address;
        this.keys = keys;
    }
}


