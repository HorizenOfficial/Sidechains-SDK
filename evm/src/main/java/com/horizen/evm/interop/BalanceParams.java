package com.horizen.evm.interop;

import com.horizen.evm.utils.Address;

import java.math.BigInteger;

public class BalanceParams extends AccountParams {
    public BigInteger amount;

    public BalanceParams(int handle, Address address, BigInteger amount) {
        super(handle, address);
        this.amount = amount;
    }
}
