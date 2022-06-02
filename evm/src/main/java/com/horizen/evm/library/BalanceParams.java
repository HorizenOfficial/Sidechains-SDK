package com.horizen.evm.library;

public class BalanceParams extends AccountParams {
    public String amount;

    public BalanceParams() {
    }

    public BalanceParams(int handle, String address, String amount) {
        super(handle, address);
        this.amount = amount;
    }
}
