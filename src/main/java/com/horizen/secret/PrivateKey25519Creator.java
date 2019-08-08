package com.horizen.secret;

import scala.Tuple2;
import scorex.crypto.signatures.Curve25519;

// NOTE: private key creator, should take in consideration settings.wallet.seed and already generated secrets of the same type in Wallet
// check SecretCreator
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
    public PrivateKey25519 generateSecret(byte[] randomSeed) {
        Tuple2<byte[], byte[]> keyPair = Curve25519.createKeyPair(randomSeed);
        return new PrivateKey25519(keyPair._1, keyPair._2);
    }
}
