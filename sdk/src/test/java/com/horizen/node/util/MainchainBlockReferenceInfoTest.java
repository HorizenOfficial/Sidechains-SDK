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
        byte[] mainchainHeaderHash = generateBytes(CommonParams.mainchainBlockHashLength());
        byte[] parentMainchainHeaderHash = generateBytes(CommonParams.mainchainBlockHashLength());
        int mainchainHeight = rndGen.nextInt();
        byte[] mainchainHeaderSidechainBlockId = generateBytes(CommonParams.sidechainIdLength());
        byte[] mainchainReferenceDataSidechainBlockId = generateBytes(CommonParams.sidechainIdLength());

        MainchainBlockReferenceInfo refInfo = new MainchainBlockReferenceInfo(mainchainHeaderHash, parentMainchainHeaderHash, mainchainHeight, mainchainHeaderSidechainBlockId, mainchainReferenceDataSidechainBlockId);

        assertArrayEquals(mainchainHeaderHash, refInfo.getMainchainHeaderHash());
        assertArrayEquals(parentMainchainHeaderHash, refInfo.getParentMainchainHeaderHash());
        assertEquals(mainchainHeight, refInfo.getMainchainHeight());
        assertArrayEquals(mainchainHeaderSidechainBlockId, refInfo.getMainchainHeaderSidechainBlockId());
        assertArrayEquals(mainchainReferenceDataSidechainBlockId, refInfo.getMainchainReferenceDataSidechainBlockId());

        assertEquals(refInfo, refInfo.serializer().parseBytes(refInfo.bytes()));
    }
}