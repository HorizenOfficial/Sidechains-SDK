package com.horizen.account.utils;

import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import static org.web3j.crypto.Sign.LOWER_REAL_V;

public final class EthereumTransactionUtils {

    private EthereumTransactionUtils() {
        // prevent instantiation
    }

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

    public static byte getRealV(BigInteger bv) {
        long v = bv.longValue();
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return (byte) v;
        }
        int inc = 0;
        if ((int) v % 2 == 0) {
            inc = 1;
        }
        return (byte) (LOWER_REAL_V + inc);
    }

    public static Sign.SignatureData createEip155PartialSignatureData(Long chainId) {
        return new Sign.SignatureData(convertToBytes(chainId), new byte[] {}, new byte[] {});
    }
}
