package com.horizen.transaction;

public enum CoreTransactionsIdsEnum {
    SidechainCoreTransactionId((byte)1),
    MC2SCAggregatedTransactionId((byte)2);

    private final byte id;

    CoreTransactionsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
