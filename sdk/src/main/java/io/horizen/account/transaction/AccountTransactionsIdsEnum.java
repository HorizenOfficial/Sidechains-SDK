package io.horizen.account.transaction;

public enum AccountTransactionsIdsEnum {
    EthereumTransactionId((byte) 1);

    private final byte id;

    AccountTransactionsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
