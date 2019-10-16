package com.horizen.transaction.mainchain;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class SidechainCreationSerializer implements SidechainRelatedMainchainOutputSerializer<SidechainCreation>
{
    private static SidechainCreationSerializer serializer;

    static {
        serializer = new SidechainCreationSerializer();
    }

    private SidechainCreationSerializer() {
        super();
    }

    public static SidechainCreationSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SidechainCreation creationOutput, Writer writer) {
        writer.putBytes(creationOutput.bytes());
    }

    @Override
    public SidechainCreation parse(Reader reader) {
        return SidechainCreation.parseBytes(reader.getBytes(reader.remaining()));
    }
}
