package com.horizen.transaction;

import io.circe.Json;
import scala.util.Try;

public class RegularTransactionJsonSerializer implements TransactionJsonSerializer<RegularTransaction> {

    private static RegularTransactionJsonSerializer serializer;

    static {
        serializer = new RegularTransactionJsonSerializer();
    }

    private RegularTransactionJsonSerializer() {
        super();
    }

    public static RegularTransactionJsonSerializer getSerializer() {
        return serializer;
    }

    @Override
    public Json toJson(RegularTransaction obj) {
        return Json.fromString("");
    }

    @Override
    public Try<RegularTransaction> parseJsonTry() {
        return null;
    }
}
