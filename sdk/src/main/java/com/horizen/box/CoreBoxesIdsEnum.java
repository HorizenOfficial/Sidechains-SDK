package com.horizen.box;

public enum CoreBoxesIdsEnum {
    RegularBoxId((byte)1),
    WithdrawalRequestBoxId((byte)2),
    ForgerBoxId((byte)3),
    CertifierRightBoxId((byte)4);

    private final byte id;

    CoreBoxesIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
