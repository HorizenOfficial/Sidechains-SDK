package io.horizen.account.transaction;

import io.horizen.account.utils.EthereumTransactionUtils;
import io.horizen.utils.BytesUtils;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class EthereumTransactionUtilsTest {
    @Test
    public void ethereumTransactionConversionUtilsTest() {
        long lv = 1997;
        long lv2 = EthereumTransactionUtils.convertToLong(BigInteger.valueOf(lv));
        assertEquals(lv, lv2);

        long lv3 = EthereumTransactionUtils.convertToLong(new BigInteger("01", 16));
        assertEquals(lv3, 1);
        // minimal encoding
        byte[] bv2 = EthereumTransactionUtils.convertToBytes(lv3);
        assertEquals("01", BytesUtils.toHexString(bv2));
    }
}
