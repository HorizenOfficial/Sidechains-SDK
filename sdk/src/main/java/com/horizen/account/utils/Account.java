package com.horizen.account.utils;

import org.web3j.crypto.Keys;

import java.math.BigInteger;

public final class Account {

    public static final int ADDRESS_SIZE = Keys.ADDRESS_LENGTH_IN_HEX / 2;

    private Account() {
        // prevent instantiation
    }
}
