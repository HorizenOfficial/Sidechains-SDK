package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

public class AccessParams extends AccountParams {
    public Address destination;

    public AccessParams(int handle, Address sender, Address destination) {
        super(handle, sender);
        this.destination = destination;
    }
}
