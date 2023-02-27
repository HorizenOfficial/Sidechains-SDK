package com.horizen.utxo.box;

public enum CoreBoxesIdsEnum {
    ZenBoxId((byte)1),
    WithdrawalRequestBoxId((byte)2),
    ForgerBoxId((byte)3);

    private final byte id;

    CoreBoxesIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
