package io.horizen.account.utils;

import java.math.BigInteger;

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
}
