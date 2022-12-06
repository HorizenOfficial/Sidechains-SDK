package com.horizen.account.utils;

import static com.horizen.account.utils.Secp256k1.ADDRESS_LENGTH_IN_HEX;

public final class Account {

    public static final int ADDRESS_SIZE = ADDRESS_LENGTH_IN_HEX / 2;

    private Account() {
        // prevent instantiation
    }
}
