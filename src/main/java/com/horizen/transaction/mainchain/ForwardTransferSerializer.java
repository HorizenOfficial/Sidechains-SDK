package com.horizen.transaction.mainchain;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ForwardTransferSerializer implements SidechainRelatedMainchainOutputSerializer<ForwardTransfer>
{
    private static ForwardTransferSerializer serializer;

    static {
        serializer = new ForwardTransferSerializer();
    }

    private ForwardTransferSerializer() {
        super();
    }

    public static ForwardTransferSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ForwardTransfer forwardTransferOutput, Writer writer) {
        writer.putBytes(forwardTransferOutput.bytes());
    }

    @Override
    public ForwardTransfer parse(Reader reader) {
        return ForwardTransfer.parseBytes(reader.getBytes(reader.remaining()));
    }
}
