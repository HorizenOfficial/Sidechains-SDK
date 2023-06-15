package io.horizen.examples.transaction;

import io.horizen.transaction.TransactionSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public final class SendVoteMessageTransactionSerializer implements TransactionSerializer<SendVoteMessageTransaction> {
    private static final SendVoteMessageTransactionSerializer serializer = new SendVoteMessageTransactionSerializer();

    private SendVoteMessageTransactionSerializer() {
        super();
    }

    public static SendVoteMessageTransactionSerializer getSerializer() {
        return serializer;
    }
    @Override
    public void serialize(SendVoteMessageTransaction transaction, Writer writer) {
        transaction.serialize(writer);
    }

    @Override
    public SendVoteMessageTransaction parse(Reader reader) {
        return SendVoteMessageTransaction.parse(reader);
    }
}
