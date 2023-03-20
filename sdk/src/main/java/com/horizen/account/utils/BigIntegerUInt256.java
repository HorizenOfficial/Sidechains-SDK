package com.horizen.account.utils;

import java.math.BigInteger;
import java.util.Arrays;

public class BigIntegerUInt256 {
    private static final int MAX_UINT256_BITS = 256;

    private final BigInteger bigInt;

    public BigIntegerUInt256(byte[] bytes) {
        if (bytes.length == 0) this.bigInt = BigInteger.ZERO;
        else {
            this.bigInt = new BigInteger(1, bytes);

            int size = this.bigInt.bitLength();

            if (size > MAX_UINT256_BITS) {
                throw new IllegalArgumentException(String.format("Bit size %d exceeds the limit of %d", size, MAX_UINT256_BITS));
            }
        }
    }

    public BigInteger getBigInt() {
        return this.bigInt;
    }

    public static byte[] getUnsignedByteArray(BigInteger bi) {
        // https://docs.oracle.com/javase/8/docs/api/java/math/BigInteger.html#toByteArray--
        // toByteArray() returns a byte array that will contain the minimum number of bytes required to represent this
        // BigInteger, including at least one sign bit, which is (ceil((this.bitLength() + 1)/8))
        // Therefore it might happen that a leading 0x00 byte is prepended.
        var barr = bi.toByteArray();
        if(barr[0] == 0x00) {
            return Arrays.copyOfRange(barr, 1, barr.length);
        } else {
            return barr;
        }
    }

}
