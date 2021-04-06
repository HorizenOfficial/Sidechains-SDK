package com.horizen.box.data;

public enum CoreBoxesDataIdsEnum {
    ZenBoxDataId((byte)1),
    WithdrawalRequestBoxDataId((byte)2),
    ForgerBoxDataId((byte)3);

    private final byte id;

    CoreBoxesDataIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
