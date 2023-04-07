package io.horizen.examples.transaction;

public enum TransactionIdsEnum {
    SendVoteToSidechainTransactionId((byte)1),
    RedeemVoteTransactionId((byte)2);
    private final byte id;
    TransactionIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
