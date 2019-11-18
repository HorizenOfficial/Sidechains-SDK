package com.horizen.utils;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import scorex.crypto.hash.Sha256;

public class Ed25519 {

    public static int keyLength() {
        return org.bouncycastle.math.ec.rfc8032.Ed25519.SECRET_KEY_SIZE;
    }

    public static int signatureLength() {
        return org.bouncycastle.math.ec.rfc8032.Ed25519.SIGNATURE_SIZE;
    }

    public static Pair<byte[], byte[]> createKeyPair(byte[] seed) {
        byte[] pvk = new byte[32];
        System.arraycopy(Sha256.hash(seed), 0, pvk, 0, 32);
        pvk[0] &= 248;
        pvk[31] &= 127;
        pvk[31] |= 64;

        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(pvk, 0);
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
        org.bouncycastle.math.ec.rfc8032.Ed25519.sign(privateKey, 0, publicKey, 0, (byte[]) null, message, 0, message.length, signature, 0);
        return signature;
    }
}
