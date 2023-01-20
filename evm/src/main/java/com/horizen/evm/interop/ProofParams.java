package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;

import java.util.Arrays;

public class ProofParams extends AccountParams {
    public Hash[] keys;

    public ProofParams(int handle, Address address, byte[][] keys) {
        super(handle, address);
        this.address = address;
        this.keys = Arrays.stream(keys).map(Hash::fromBytes).toArray(Hash[]::new);
    }
}


