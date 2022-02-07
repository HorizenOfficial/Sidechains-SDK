package com.horizen.customtypes;

import com.horizen.transaction.TransactionSerializer;
import scala.util.Success;
import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

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
    public Try<SemanticallyInvalidTransaction> parseBytesTry(byte[] bytes) {
        return new Success<>(new SemanticallyInvalidTransaction());
    }

    @Override
    public void serialize(SemanticallyInvalidTransaction obj, Writer writer) {}

    @Override
    public SemanticallyInvalidTransaction parse(Reader reader) {
        return new SemanticallyInvalidTransaction();
    }
}
