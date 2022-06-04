package com.horizen.evm.interop;

public class NonceParams extends AccountParams {
    public long nonce;

    public NonceParams() {
    }

    public NonceParams(int handle, byte[] address, long nonce) {
        super(handle, address);
        this.nonce = nonce;
    }
}
