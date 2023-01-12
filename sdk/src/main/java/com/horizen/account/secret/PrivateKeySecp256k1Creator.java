package com.horizen.account.secret;

import com.horizen.account.utils.Secp256k1;
import com.horizen.secret.SecretCreator;

public final class PrivateKeySecp256k1Creator implements SecretCreator<PrivateKeySecp256k1> {
    private static PrivateKeySecp256k1Creator instance;

    private final String domain = "PrivateKeySecp25519k1";

    static {
        instance = new PrivateKeySecp256k1Creator();
    }

    private PrivateKeySecp256k1Creator() {
        super();
    }

    public static PrivateKeySecp256k1Creator getInstance() {
        return instance;
    }

    @Override
    public PrivateKeySecp256k1 generateSecret(byte[] seed) {
        var keyPair = Secp256k1.createKeyPair(seed);

        return new PrivateKeySecp256k1(keyPair.getKey());
    }

    @Override
    public byte[] salt() {
        return domain.getBytes();
    }
}
