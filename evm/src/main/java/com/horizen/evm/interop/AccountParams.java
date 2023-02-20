package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

public class AccountParams extends HandleParams {
    public final Address address;

    public AccountParams(int handle, Address address) {
        super(handle);
        this.address = address;
    }
}
