package com.horizen.account.utils;

public final class Secp256k1 {
    private static final int COORD_SIZE = 32;
    public static final int PRIVATE_KEY_SIZE = COORD_SIZE;
    public static final int PUBLIC_KEY_SIZE = COORD_SIZE * 2;

    public static final int SIGNATURE_V_SIZE = 1;
    public static final int SIGNATURE_RS_SIZE = 32;
    public static final int SIGNATURE_SIZE = SIGNATURE_V_SIZE + SIGNATURE_RS_SIZE * 2;

    private Secp256k1() {
        // prevent instantiation
    }
}
