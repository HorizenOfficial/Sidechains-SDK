package io.horizen.account.transaction;

import io.horizen.account.utils.EthereumTransactionUtils;
import io.horizen.utils.BytesUtils;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class EthereumTransactionUtilsTest {
    @Test
    public void ethereumTransactionConversionUtilsTest() {

        long lv3 = (new BigInteger("0001", 16)).longValueExact();
        assertEquals(lv3, 1);
        // minimal encoding
        byte[] bv2 = EthereumTransactionUtils.convertToBytes(lv3);
        assertEquals("01", BytesUtils.toHexString(bv2));
    }
}
