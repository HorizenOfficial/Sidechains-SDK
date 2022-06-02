package com.horizen.evm.library;

public class NonceParams extends AccountParams {
    public long nonce;

    public NonceParams() {
    }

    public NonceParams(int handle, String address, long nonce) {
        super(handle, address);
        this.nonce = nonce;
    }
}
