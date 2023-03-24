package io.horizen.account.secret;

import io.horizen.account.utils.Secp256k1;
import io.horizen.secret.SecretCreator;
import java.nio.charset.StandardCharsets;


public final class PrivateKeySecp256k1Creator implements SecretCreator<PrivateKeySecp256k1> {
    private static final PrivateKeySecp256k1Creator instance;
    private static final byte[] domain = "PrivateKeySecp256k1".getBytes(StandardCharsets.UTF_8);

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
        return domain;
    }
}
