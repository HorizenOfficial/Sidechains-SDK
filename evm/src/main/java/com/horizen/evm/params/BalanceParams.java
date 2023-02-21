package com.horizen.evm.params;

import com.horizen.evm.Address;

import java.math.BigInteger;

public class BalanceParams extends AccountParams {
    public final BigInteger amount;

    public BalanceParams(int handle, Address address, BigInteger amount) {
        super(handle, address);
        this.amount = amount;
    }
}
