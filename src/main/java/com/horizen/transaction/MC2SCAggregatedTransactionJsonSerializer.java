package com.horizen.transaction;

import io.circe.Json;
import scala.util.Try;

public class MC2SCAggregatedTransactionJsonSerializer implements TransactionJsonSerializer<MC2SCAggregatedTransaction> {

    private static MC2SCAggregatedTransactionJsonSerializer serializer;

    static {
        serializer = new MC2SCAggregatedTransactionJsonSerializer();
    }

    private MC2SCAggregatedTransactionJsonSerializer() {
        super();
    }

    public static MC2SCAggregatedTransactionJsonSerializer getSerializer() {
        return serializer;
    }

    @Override
    public Json toJson(MC2SCAggregatedTransaction obj) {
        return Json.fromString("");
    }

    @Override
    public Try<MC2SCAggregatedTransaction> parseJsonTry() {
        return null;
    }
}
