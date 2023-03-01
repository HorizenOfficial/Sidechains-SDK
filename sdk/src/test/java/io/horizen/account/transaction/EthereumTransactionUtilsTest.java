package io.horizen.account.transaction;

import io.horizen.account.utils.EthereumTransactionUtils;
import io.horizen.utils.BytesUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EthereumTransactionUtilsTest {
    @Test
    public void ethereumTransactionConversionUtilsTest() {
        long lv = 1997;
        byte[] res = EthereumTransactionUtils.convertToBytes(lv);
        long lv2 = EthereumTransactionUtils.convertToLong(res);
        assertEquals(lv, lv2);

        byte[] bv = BytesUtils.fromHexString("01");
        long lv3 = EthereumTransactionUtils.convertToLong(bv);
        assertEquals(lv3, 1);
        // minimal encoding
        byte[] bv2 = EthereumTransactionUtils.convertToBytes(lv3);
        assertEquals("01", BytesUtils.toHexString(bv2));
    }
}
