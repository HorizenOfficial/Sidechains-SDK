package com.horizen.transaction;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ForgingStakeTransactionSerializer implements TransactionSerializer<ForgingStakeTransaction>
{
    private static ForgingStakeTransactionSerializer serializer;

    static {
        serializer = new ForgingStakeTransactionSerializer();
    }

    private ForgingStakeTransactionSerializer() {
        super();
    }

    public static ForgingStakeTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ForgingStakeTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public ForgingStakeTransaction parse(Reader reader) {
        return ForgingStakeTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }

}
