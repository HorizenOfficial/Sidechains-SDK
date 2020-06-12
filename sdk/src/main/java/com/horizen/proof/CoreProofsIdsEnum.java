package com.horizen.proof;

public enum CoreProofsIdsEnum {
    Signature25519Id((byte)1),
    VrfProofId((byte)2),
    SchnorrSignatureId((byte)3);

    private final byte id;

    CoreProofsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
