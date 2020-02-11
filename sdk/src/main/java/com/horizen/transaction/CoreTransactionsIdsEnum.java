package com.horizen.transaction;

public enum CoreTransactionsIdsEnum {
    RegularTransactionId((byte)1),
    MC2SCAggregatedTransactionId((byte)2),
    SidechainCoreTransactionId((byte)3);

    private final byte id;

    CoreTransactionsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
