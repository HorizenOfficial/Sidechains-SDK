package com.horizen.node.util;

import com.horizen.CommonParams;
import org.junit.Test;

import static org.junit.Assert.*;

public class MainchainBlockReferenceInfoTest {
    private scala.util.Random rndGen = new scala.util.Random();

    private byte[] generateBytes(int size) {
        byte [] res = new byte[size];
        rndGen.nextBytes(res);
        return res;
    }

    @Test
    public void serializationTest() {
        byte[] mainchainBlockReferenceHash = generateBytes(CommonParams.mainchainBlockHashLength());
        byte[] parentMainchainBlockReferenceHash = generateBytes(CommonParams.mainchainBlockHashLength());
        int mainchainHeight = rndGen.nextInt();
        byte[] sidechainBlockId = generateBytes(CommonParams.sidechainIdLength());

        MainchainBlockReferenceInfo refInfo = new MainchainBlockReferenceInfo(mainchainBlockReferenceHash, parentMainchainBlockReferenceHash, mainchainHeight, sidechainBlockId);

        assertArrayEquals(mainchainBlockReferenceHash, refInfo.getMainchainBlockReferenceHash());
        assertArrayEquals(parentMainchainBlockReferenceHash, refInfo.getParentMainchainBlockReferenceHash());
        assertEquals(mainchainHeight, refInfo.getMainchainHeight());
        assertArrayEquals(sidechainBlockId, refInfo.getSidechainBlockId());

        assertEquals(refInfo, refInfo.serializer().parseBytes(refInfo.bytes()));
    }
}