package com.horizen.transaction;

import io.circe.Json;
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

    @Override
    public void serialize(RegularTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public RegularTransaction parse(Reader reader) {
        return RegularTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }

    @Override
    public Json toJson(RegularTransaction transaction) {
        return transaction.toJson();
    }

    @Override
    public RegularTransaction parseJson(Json json) {
        return RegularTransaction.parseJson(json);
    }
}

