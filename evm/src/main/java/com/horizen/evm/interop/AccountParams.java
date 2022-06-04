package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

public class AccountParams extends HandleParams {
    public Address address;

    public AccountParams() {
    }

    public AccountParams(int handle, byte[] address) {
        super(handle);
        this.address = Address.FromBytes(address);
    }
}
