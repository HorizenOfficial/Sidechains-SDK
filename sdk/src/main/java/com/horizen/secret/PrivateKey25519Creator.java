package com.horizen.secret;

import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import java.nio.charset.StandardCharsets;

public final class PrivateKey25519Creator implements SecretCreator<PrivateKey25519>
{
    private static final PrivateKey25519Creator instance;
    private static final byte[] domain = "PrivateKey25519".getBytes(StandardCharsets.UTF_8);

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
