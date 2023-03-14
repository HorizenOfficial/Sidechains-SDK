package com.horizen.cryptolibprovider;

import com.horizen.cryptolibprovider.utils.CumulativeHashFunctions;
import com.horizen.utils.BytesUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class CumulativeHashFunctionsTest {

    @Test
    public void mainchainRegression() {
        String prevScCumTreeHashHex = "7a77853a377d41ba97407b033c1018da345cdc8a8dce2346b4f218d38851d312";
        // Note: scTxsCommitmentHex value (unit256) is represented in BigEndian
        String scTxsCommitmentHex = "0d65e96cc400f0e6537f6391d812c1959dd46e958928d2338b75d4662f01d466";

        String expectedScCumTreeHashHex = "791683fb2d0550b15ed99cdb7ba10e0fe6227a6850f41cbad0a84268b902ea25";

        byte[] res = CumulativeHashFunctions.computeCumulativeHash(
                BytesUtils.fromHexString(prevScCumTreeHashHex),
                BytesUtils.reverseBytes(BytesUtils.fromHexString(scTxsCommitmentHex))
        );

        assertEquals("CumulativeHash result is different to the MC one.",
                expectedScCumTreeHashHex, BytesUtils.toHexString(res));
    }
}
