package com.horizen.node.util;

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
        writer.putBytes(referenceInfo.bytes());
    }

    @Override
    public MainchainBlockReferenceInfo parse(Reader reader) {
        return MainchainBlockReferenceInfo.parseBytes(reader.getBytes(reader.remaining()));
    }
}
