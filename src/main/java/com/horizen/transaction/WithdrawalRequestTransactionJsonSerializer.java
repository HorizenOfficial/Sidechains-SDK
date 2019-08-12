package com.horizen.transaction;

import io.circe.Json;
import scala.util.Try;

public class WithdrawalRequestTransactionJsonSerializer implements TransactionJsonSerializer<WithdrawalRequestTransaction> {

    private static WithdrawalRequestTransactionJsonSerializer serializer;

    static {
        serializer = new WithdrawalRequestTransactionJsonSerializer();
    }

    private WithdrawalRequestTransactionJsonSerializer() {
        super();
    }

    public static WithdrawalRequestTransactionJsonSerializer getSerializer() {
        return serializer;
    }

    @Override
    public Json toJson(WithdrawalRequestTransaction obj) {
        return null;
    }

    @Override
    public Try<WithdrawalRequestTransaction> parseJsonTry() {
        return null;
    }
}
