package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

public class AccessParams extends AccountParams {
    public Address destination;

    public AccessParams(int handle, byte[] sender, byte[] destination) {
        super(handle, sender);
        this.destination = Address.fromBytes(destination);
    }
}
