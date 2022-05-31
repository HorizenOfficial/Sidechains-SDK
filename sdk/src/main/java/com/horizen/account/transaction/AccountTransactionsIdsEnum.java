package com.horizen.account.transaction;

public enum AccountTransactionsIdsEnum {
    EthereumTransaction((byte) 2);

    private final byte id;

    AccountTransactionsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
