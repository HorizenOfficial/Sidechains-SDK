package com.horizen.secret;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.node.NodeWallet;
import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;
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
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(seed);
        return new PrivateKey25519(keyPair._1, keyPair._2);
    }

    @Override
    public PrivateKey25519 generateNextSecret(NodeWallet wallet) {
        List<Secret> prevSecrets = wallet.secretsOfType(PrivateKey25519.class);
        byte[] nonce = Ints.toByteArray(prevSecrets.size());
        byte[] seed = Blake2b256.hash(Bytes.concat(wallet.walletSeed(), nonce));

        return generateSecret(seed);
    }
}
