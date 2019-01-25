package com.horizen.transaction;

import scala.util.Try;


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
    public byte[] toBytes(RegularTransaction transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<RegularTransaction> parseBytes(byte[] bytes) {
        return RegularTransaction.parseBytes(bytes);
    }
}

