package com.horizen.box.data;

public enum CoreBoxesDataIdsEnum {
    RegularBoxDataId((byte)1),
    WithdrawalRequestBoxDataId((byte)2),
    ForgerBoxDataId((byte)3),
    CertifierRightBoxDataId((byte)4);

    private final byte id;

    CoreBoxesDataIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
