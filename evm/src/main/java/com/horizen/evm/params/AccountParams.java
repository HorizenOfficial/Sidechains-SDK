package com.horizen.evm.params;

import com.horizen.evm.Address;

public class AccountParams extends HandleParams {
    public final Address address;

    public AccountParams(int handle, Address address) {
        super(handle);
        this.address = address;
    }
}
