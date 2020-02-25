package com.horizen.transaction;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;


public final class RegularTransactionSerializer implements TransactionSerializer<RegularTransaction>
{
    private static RegularTransactionSerializer serializer;

    static {
        serializer = new RegularTransactionSerializer();
    }

    private RegularTransactionSerializer() {
        super();
    }

    public static RegularTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(RegularTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public RegularTransaction parse(Reader reader) {
        return RegularTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }

}

