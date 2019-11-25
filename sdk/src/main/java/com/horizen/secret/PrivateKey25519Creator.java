package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.node.NodeWallet;
import com.horizen.utils.Ed25519;
import com.horizen.utils.Pair;
import scorex.crypto.hash.Blake2b256;

import java.util.List;

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
    public PrivateKey25519 generateNextSecret(NodeWallet wallet) {
        List<Secret> prevSecrets = wallet.secretsOfType(PrivateKey25519.class);
        byte[] nonce = Ints.toByteArray(prevSecrets.size());
        byte[] seed = Blake2b256.hash(Bytes.concat(wallet.walletSeed(), nonce));

        return generateSecret(seed);
    }
}
