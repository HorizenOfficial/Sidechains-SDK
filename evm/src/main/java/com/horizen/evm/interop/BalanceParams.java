package com.horizen.evm.interop;

import java.math.BigInteger;

public class BalanceParams extends AccountParams {
    public BigInteger amount;

    public BalanceParams(int handle, byte[] address, BigInteger amount) {
        super(handle, address);
        this.amount = amount;
    }
}
