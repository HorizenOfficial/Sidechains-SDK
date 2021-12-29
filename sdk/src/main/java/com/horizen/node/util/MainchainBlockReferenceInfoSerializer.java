package com.horizen.node.util;

import com.horizen.CommonParams;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;


public class MainchainBlockReferenceInfoSerializer implements ScorexSerializer<MainchainBlockReferenceInfo> {
    private static MainchainBlockReferenceInfoSerializer serializer;

    static {
        serializer = new MainchainBlockReferenceInfoSerializer();
    }

    private MainchainBlockReferenceInfoSerializer() {
        super();
    }

    public static ScorexSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(MainchainBlockReferenceInfo referenceInfo, Writer writer) {
        writer.putBytes(referenceInfo.getMainchainHeaderHash());
        writer.putBytes(referenceInfo.getParentMainchainHeaderHash());
        writer.putInt(referenceInfo.getMainchainHeight());
        writer.putBytes(referenceInfo.getMainchainHeaderSidechainBlockId());
        writer.putBytes(referenceInfo.getMainchainReferenceDataSidechainBlockId());
    }

    @Override
    public MainchainBlockReferenceInfo parse(Reader reader) {
        byte[] mainchainBlockReferenceHash = reader.getBytes(CommonParams.mainchainBlockHashLength());
        byte[] parentMainchainBlockReferenceHash = reader.getBytes(CommonParams.mainchainBlockHashLength());
        int mainchainHeight = reader.getInt();
        byte[] mainchainHeaderSidechainBlockId = reader.getBytes(CommonParams.sidechainIdLength());
        byte[] mainchainReferenceDataSidechainBlockId = reader.getBytes(CommonParams.sidechainIdLength());

        return new MainchainBlockReferenceInfo(mainchainBlockReferenceHash,
                                               parentMainchainBlockReferenceHash,
                                               mainchainHeight,
                                               mainchainHeaderSidechainBlockId,
                                               mainchainReferenceDataSidechainBlockId);

    }
}
