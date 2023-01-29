package com.horizen.account.secret;

import com.horizen.account.utils.Secp256k1;
import com.horizen.secret.SecretCreator;

import java.nio.charset.StandardCharsets;

public final class PrivateKeySecp256k1Creator implements SecretCreator<PrivateKeySecp256k1> {
    private static final PrivateKeySecp256k1Creator instance;

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
        assert keyPair != null;
        return new PrivateKeySecp256k1(keyPair.getKey());
    }

    /**
     * Method to get salt.
     * In this case salt serves as a domain separation
     *
     * @return salt as byte array in UTF-8 encoding
     */
    @Override
    public byte[] salt() {
        String domain = "PrivateKeySecp25519k1";
        return domain.getBytes(StandardCharsets.UTF_8);
    }
}
