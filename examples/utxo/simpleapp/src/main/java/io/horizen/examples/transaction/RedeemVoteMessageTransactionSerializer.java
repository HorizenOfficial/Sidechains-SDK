package io.horizen.examples.transaction;

import io.horizen.transaction.TransactionSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public final class RedeemVoteMessageTransactionSerializer implements TransactionSerializer<RedeemVoteMessageTransaction> {
    private static final RedeemVoteMessageTransactionSerializer serializer = new RedeemVoteMessageTransactionSerializer();

    private RedeemVoteMessageTransactionSerializer() {
        super();
    }

    public static RedeemVoteMessageTransactionSerializer getSerializer() {
        return serializer;
    }
    @Override
    public void serialize(RedeemVoteMessageTransaction transaction, Writer writer) {
        transaction.serialize(writer);
    }

    @Override
    public RedeemVoteMessageTransaction parse(Reader reader) {
        return RedeemVoteMessageTransaction.parse(reader);
    }
}
