package com.horizen.account.utils;

public final class Secp256k1 {
    private static final int COORD_SIZE = 32;
    public static final int PRIVATE_KEY_SIZE = COORD_SIZE;
    public static final int PUBLIC_KEY_SIZE = COORD_SIZE * 2;

    // signature's v might be set to {0,1} + CHAIN_ID * 2 + 35 (see EIP155)
    public static final int SIGNATURE_V_MAXSIZE = Long.BYTES;
    public static final int SIGNATURE_RS_SIZE = 32;

    private Secp256k1() {
        // prevent instantiation
    }
}
