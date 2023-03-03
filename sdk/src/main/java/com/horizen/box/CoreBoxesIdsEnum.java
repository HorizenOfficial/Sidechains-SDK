package com.horizen.box;

public enum CoreBoxesIdsEnum {
    ZenBoxId((byte)1),
    WithdrawalRequestBoxId((byte)2),
    ForgerBoxId((byte)3),
    CrossChainMessageBoxId((byte)4),
    CrossChainRedeemMessageBoxId((byte)5);

    private final byte id;

    CoreBoxesIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}