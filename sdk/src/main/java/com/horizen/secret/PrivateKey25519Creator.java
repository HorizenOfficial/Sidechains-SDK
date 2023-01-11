package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.node.NodeWallet;
import com.horizen.node.NodeWalletBase;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import scorex.crypto.hash.Blake2b256;

import java.util.List;

public final class PrivateKey25519Creator implements SecretCreator<PrivateKey25519>
{
    private static final PrivateKey25519Creator instance;
    private final String domain = "PrivateKey25519";

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
        return domain.getBytes();
    }
}
