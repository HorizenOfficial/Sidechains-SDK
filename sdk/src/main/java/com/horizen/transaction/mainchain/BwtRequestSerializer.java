package com.horizen.transaction.mainchain;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class BwtRequestSerializer implements SidechainRelatedMainchainOutputSerializer<BwtRequest>
{
    private static BwtRequestSerializer serializer;

    static {
        serializer = new BwtRequestSerializer();
    }

    private BwtRequestSerializer() {
        super();
    }

    public static BwtRequestSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(BwtRequest bwtRequestOutput, Writer writer) {
        writer.putBytes(bwtRequestOutput.bytes());
    }

    @Override
    public BwtRequest parse(Reader reader) {
        return BwtRequest.parseBytes(reader.getBytes(reader.remaining()));
    }
}