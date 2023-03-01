package io.horizen.secret;

public enum SecretsIdsEnum {
    PrivateKey25519SecretId((byte)0),
    VrfPrivateKeySecretId((byte)3),
    SchnorrSecretKeyId((byte)4),
    PrivateKeySecp256k1SecretId((byte)5);


    private final byte id;

    SecretsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
