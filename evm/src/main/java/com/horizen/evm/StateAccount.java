package com.horizen.evm;

public class StateAccount {
    public long nonce;
    public String balance;

    @Override
    public String toString() {
        return String.format("StateAccount{Nonce=%d, Balance='%s'}", nonce, balance);
    }
}
