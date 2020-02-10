package com.horizen.proof;

public enum CoreProofsIdsEnum {
    Signature25519Id((byte)1);

    private final byte id;

    CoreProofsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
