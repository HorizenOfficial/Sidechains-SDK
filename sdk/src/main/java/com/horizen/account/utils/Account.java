package com.horizen.account.utils;

import org.web3j.crypto.ContractUtils;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

public final class Account {
    // add constant for uint256 max bit size as byte size is 33 for the uint256 max value
    // and a byte size check of that would include values above the uint256 max value
    public static final int BIG_INT_MAX_BIT_SIZE = 256;
    public static final int ADDRESS_LENGTH_IN_HEX = 40;
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
