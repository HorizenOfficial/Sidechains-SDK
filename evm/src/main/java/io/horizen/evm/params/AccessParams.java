package io.horizen.evm.params;

import io.horizen.evm.Address;

public class AccessParams extends AccountParams {
    public final Address destination;

    public AccessParams(int handle, Address sender, Address destination) {
        super(handle, sender);
        this.destination = destination;
    }
}
