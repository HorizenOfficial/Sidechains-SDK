package com.horizen.transaction;

import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;


public class RegularTransactionSerializer implements TransactionSerializer<RegularTransaction>
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

    /*
    @Override
    public byte[] toBytes(RegularTransaction transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<RegularTransaction> parseBytesTry(byte[] bytes) {
        return RegularTransaction.parseBytes(bytes);
    }
    */

    @Override
    public void serialize(RegularTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public RegularTransaction parse(Reader reader) {
        return RegularTransaction.parseBytes(reader.getBytes(reader.remaining())).get();
    }
}

