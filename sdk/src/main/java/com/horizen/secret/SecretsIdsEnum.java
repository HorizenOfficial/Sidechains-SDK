package com.horizen.secret;

public enum SecretsIdsEnum {
    PrivateKey25519SecretId((byte)0),
    VrfPrivateKeySecretId((byte)3),
    SchnorrSecretKeyId((byte)4);

    private final byte id;

    SecretsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
