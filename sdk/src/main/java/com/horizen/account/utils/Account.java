package com.horizen.account.utils;

import org.web3j.crypto.Keys;

import java.math.BigInteger;

public final class Account {

    public static final int ADDRESS_SIZE = Keys.ADDRESS_LENGTH_IN_HEX / 2;
    public static final int GAS_LIMIT = 30000000;
    public static final BigInteger INITIAL_BASE_FEE = BigInteger.valueOf(1000000000);

    private Account() {
        // prevent instantiation
    }
}
