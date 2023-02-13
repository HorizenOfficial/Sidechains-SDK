package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class NonceParams extends AccountParams {
    public BigInteger nonce;

    public NonceParams(int handle, Address address, BigInteger nonce) {
        super(handle, address);
        this.nonce = nonce;
    }
}
