package com.horizen.secret;

import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;

public final class PrivateKey25519Creator implements SecretCreator<PrivateKey25519>
{
    private static PrivateKey25519Creator instance;

    static {
        instance = new PrivateKey25519Creator();
    }

    private PrivateKey25519Creator() {
        super();
    }

    public static PrivateKey25519Creator getInstance() {
        return instance;
    }

    @Override
    public PrivateKey25519 generateSecret(byte[] seed) {
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(seed);
        return new PrivateKey25519(keyPair.getKey(), keyPair.getValue());
    }

    @Override
    public byte[] salt() {
        return "PrivateKey25519".getBytes();
    }
}
