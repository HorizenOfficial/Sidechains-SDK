package com.horizen.evm.library;

import com.horizen.evm.utils.Address;

public class NonceParams extends AccountParams {
    public long nonce;

    public NonceParams() {
    }

    public NonceParams(int handle, byte[] address, long nonce) {
        super(handle, address);
        this.nonce = nonce;
    }
}
