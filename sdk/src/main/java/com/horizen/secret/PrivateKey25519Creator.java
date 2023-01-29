package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.node.NodeWallet;
import com.horizen.node.NodeWalletBase;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import scorex.crypto.hash.Blake2b256;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PrivateKey25519Creator implements SecretCreator<PrivateKey25519>
{
    private static final PrivateKey25519Creator instance;

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
        String domain = "PrivateKey25519";
        return domain.getBytes(StandardCharsets.UTF_8);
    }
}
