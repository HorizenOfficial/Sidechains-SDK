package com.horizen.evm.library;

public class AccountParams extends HandleParams {
    public String address;

    public AccountParams() {
    }

    public AccountParams(int handle, String address) {
        super(handle);
        this.address = address;
    }
}
