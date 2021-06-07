package com.horizen.transaction;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class SidechainCoreTransactionSerializer implements TransactionSerializer<SidechainCoreTransaction>
{
    private static SidechainCoreTransactionSerializer serializer;

    static {
        serializer = new SidechainCoreTransactionSerializer();
    }

    private SidechainCoreTransactionSerializer() {
        super();
    }

    public static SidechainCoreTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SidechainCoreTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public SidechainCoreTransaction parse(Reader reader) {
        return SidechainCoreTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }
}
