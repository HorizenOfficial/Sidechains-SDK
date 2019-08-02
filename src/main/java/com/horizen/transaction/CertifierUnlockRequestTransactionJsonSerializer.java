package com.horizen.transaction;

import io.circe.Json;
import scala.util.Try;

public class CertifierUnlockRequestTransactionJsonSerializer implements TransactionJsonSerializer<CertifierUnlockRequestTransaction> {

    private static CertifierUnlockRequestTransactionJsonSerializer serializer;

    static {
        serializer = new CertifierUnlockRequestTransactionJsonSerializer();
    }

    private CertifierUnlockRequestTransactionJsonSerializer() {
        super();
    }

    public static CertifierUnlockRequestTransactionJsonSerializer getSerializer() {
        return serializer;
    }

    @Override
    public Json toJson(CertifierUnlockRequestTransaction obj) {
        return Json.fromString("");
    }

    @Override
    public Try<CertifierUnlockRequestTransaction> tryParseJson() {
        return null;
    }
}
