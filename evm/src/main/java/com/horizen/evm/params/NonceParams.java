package com.horizen.evm.params;

import com.horizen.evm.Address;

import java.math.BigInteger;

public class NonceParams extends AccountParams {
    public final BigInteger nonce;

    public NonceParams(int handle, Address address, BigInteger nonce) {
        super(handle, address);
        this.nonce = nonce;
    }
}
