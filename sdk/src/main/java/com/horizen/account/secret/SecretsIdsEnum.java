package com.horizen.account.secret;

public enum SecretsIdsEnum {
    PrivateKeySecp256k1SecretId((byte) 10);

    private final byte id;

    SecretsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
