package com.horizen.utils;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import java.security.SecureRandom;

public final class Ed25519 {

    private Ed25519() {
    }

    public static int privateKeyLength() {
        return org.bouncycastle.math.ec.rfc8032.Ed25519.SECRET_KEY_SIZE;
    }
    public static int publicKeyLength() {
        return org.bouncycastle.math.ec.rfc8032.Ed25519.PUBLIC_KEY_SIZE;
    }
    public static int signatureLength() {
        return org.bouncycastle.math.ec.rfc8032.Ed25519.SIGNATURE_SIZE;
    }

    public static Pair<byte[], byte[]> createKeyPair(byte[] seed) {
        SecureRandom rnd = ChaChaPrngSecureRandom.getInstance(seed);
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(rnd);
        Ed25519PublicKeyParameters publicKey = privateKey.generatePublicKey();
        return new Pair<>(privateKey.getEncoded(), publicKey.getEncoded());
    }

    public static boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        try {
            return org.bouncycastle.math.ec.rfc8032.Ed25519.verify(signature, 0, publicKey, 0, message, 0, message.length);
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] sign(byte[] privateKey, byte[] message, byte[] publicKey) {
        byte[] signature = new byte[64];
        org.bouncycastle.math.ec.rfc8032.Ed25519.sign(privateKey, 0, publicKey, 0, null, message, 0, message.length, signature, 0);
        return signature;
    }
}
