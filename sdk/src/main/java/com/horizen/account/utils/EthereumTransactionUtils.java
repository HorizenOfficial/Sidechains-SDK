package com.horizen.account.utils;

import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public final class EthereumTransactionUtils {

    // Util function
    // w3j private method in TransactionEncoder, it returns a byte array with Long.BYTES length
    public static byte[] convertToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    // w3j way for converting bytes, it works also with generic byte contents (not only Long.BYTES byte arrays)
    public static long convertToLong(byte[] bytes) {
        BigInteger bi = Numeric.toBigInt(bytes);
        return bi.longValueExact();
    }

    private EthereumTransactionUtils() {
        // prevent instantiation
    }
}