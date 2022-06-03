package com.horizen.evm.library;

import java.math.BigInteger;

public class BalanceParams extends AccountParams {
    public BigInteger amount;

    public BalanceParams() {
    }

    public BalanceParams(int handle, String address, BigInteger amount) {
        super(handle, address);
        this.amount = amount;
    }
}
