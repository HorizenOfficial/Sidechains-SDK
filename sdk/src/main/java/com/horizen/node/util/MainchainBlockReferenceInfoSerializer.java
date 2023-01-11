package com.horizen.node.util;

import com.horizen.params.CommonParams;
import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;


public class MainchainBlockReferenceInfoSerializer implements SparkzSerializer<MainchainBlockReferenceInfo> {
    private static MainchainBlockReferenceInfoSerializer serializer;

    static {
        serializer = new MainchainBlockReferenceInfoSerializer();
    }

    private MainchainBlockReferenceInfoSerializer() {
        super();
    }

    public static SparkzSerializer getSerializer() {
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
