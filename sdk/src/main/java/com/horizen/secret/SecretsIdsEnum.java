package com.horizen.secret;

public enum SecretsIdsEnum {
    PrivateKey25519Secret((byte)0),
    CustomPrivateKey((byte)1),
    VrfPrivateKey((byte)3);

    private final byte id;

    SecretsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
