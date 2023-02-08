package com.horizen.account.transaction;

import com.horizen.account.utils.EthereumTransactionDecoder;
import com.horizen.transaction.TransactionSerializer;
import org.web3j.utils.Numeric;
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

    // Maybe we need to do the serialization in a different way to be eth compatible,
    // because of here used message length integer needed for decoding
    @Override
    public void serialize(EthereumTransaction tx, Writer writer) {
        tx.encode(tx.isSigned(), writer);
    }

    @Override
    public EthereumTransaction parse(Reader reader) {
        return EthereumTransactionDecoder.decode(reader);
    }
}
