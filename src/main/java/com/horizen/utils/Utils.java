package com.horizen.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Utils
{
    private Utils() {}

    public static final int SHA256_LENGTH = 32;

    public static byte[] doubleSHA256Hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, bytes.length);
            byte[] first = digest.digest();
            return digest.digest(first);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    public static byte[] doubleSHA256HashOfConcatenation(byte[] bytes1, byte[] bytes2) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes1, 0, bytes1.length);
            digest.update(bytes2, 0, bytes2.length);
            byte[] first = digest.digest();
            return digest.digest(first);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }
}
