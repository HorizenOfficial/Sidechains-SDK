package com.horizen.customtypes;

import com.horizen.transaction.TransactionSerializer;
import scala.util.Try;

public class SemanticallyInvalidTransactionSerializer implements TransactionSerializer<SemanticallyInvalidTransaction> {

    private static SemanticallyInvalidTransactionSerializer serializer;

    static {
        serializer = new SemanticallyInvalidTransactionSerializer();
    }

    private SemanticallyInvalidTransactionSerializer() {
        super();
    }

    public static SemanticallyInvalidTransactionSerializer getSerializer() {
        return serializer;
    }


    @Override
    public byte[] toBytes(SemanticallyInvalidTransaction obj) {
        return obj.bytes();
    }

    @Override
    public Try<SemanticallyInvalidTransaction> parseBytes(byte[] bytes) {
        return SemanticallyInvalidTransaction.parseBytes(bytes);
    }
}
