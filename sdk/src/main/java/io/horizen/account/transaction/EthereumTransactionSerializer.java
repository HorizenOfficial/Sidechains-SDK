package io.horizen.account.transaction;

import io.horizen.account.utils.EthereumTransactionDecoder;
import io.horizen.transaction.TransactionSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public class EthereumTransactionSerializer implements TransactionSerializer<EthereumTransaction> {

    private static final EthereumTransactionSerializer serializer;

    static {
        serializer = new EthereumTransactionSerializer();
    }

    private EthereumTransactionSerializer() {
        super();
    }

    public static EthereumTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(EthereumTransaction tx, Writer writer) {
        tx.encode(true, writer);
    }

    @Override
    public EthereumTransaction parse(Reader reader) {
        return EthereumTransactionDecoder.decode(reader);
    }
}
