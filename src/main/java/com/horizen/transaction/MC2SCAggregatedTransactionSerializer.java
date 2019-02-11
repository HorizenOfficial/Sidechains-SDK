package com.horizen.transaction;

import scala.util.Try;

public final class MC2SCAggregatedTransactionSerializer implements TransactionSerializer<MC2SCAggregatedTransaction>
{
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
    public byte[] toBytes(MC2SCAggregatedTransaction transaction) {
        return transaction.bytes();
    }

    @Override
    public Try<MC2SCAggregatedTransaction> parseBytes(byte[] bytes) {
        return MC2SCAggregatedTransaction.parseBytes(bytes);
    }
}

