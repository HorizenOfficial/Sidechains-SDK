package com.horizen.transaction;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class MC2SCAggregatedTransactionSerializer implements TransactionSerializer<MC2SCAggregatedTransaction> {
    private static MC2SCAggregatedTransactionSerializer serializer;

    static {
        serializer = new MC2SCAggregatedTransactionSerializer();
    }

    private MC2SCAggregatedTransactionSerializer() {
        super();
    }

    public static MC2SCAggregatedTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(MC2SCAggregatedTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public MC2SCAggregatedTransaction parse(Reader reader) {
        return MC2SCAggregatedTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }
}

