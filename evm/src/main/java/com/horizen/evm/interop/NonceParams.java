package com.horizen.evm.interop;

import java.math.BigInteger;

public class NonceParams extends AccountParams {
    public BigInteger nonce;

    public NonceParams() {
    }

    public NonceParams(int handle, byte[] address, BigInteger nonce) {
        super(handle, address);
        this.nonce = nonce;
    }
}
