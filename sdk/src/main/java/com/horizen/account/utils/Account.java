package com.horizen.account.utils;

import org.web3j.crypto.ContractUtils;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

import static com.horizen.account.utils.Secp256k1.ADDRESS_LENGTH_IN_HEX;

public final class Account {

    public static final int ADDRESS_SIZE = ADDRESS_LENGTH_IN_HEX / 2;

    private Account() {
        // prevent instantiation
    }

    public static byte[] generateContractAddress(byte[] fromAddress, BigInteger nonce) {
        return ContractUtils.generateContractAddress(fromAddress, nonce);
    }

    public static String checksumAddress(byte[] address) {
        return Keys.toChecksumAddress(Numeric.toHexString(address));
    }
}
