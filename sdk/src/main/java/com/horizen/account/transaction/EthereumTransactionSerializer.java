package com.horizen.account.transaction;

import com.horizen.account.utils.EthereumTransactionDecoder;
import com.horizen.transaction.TransactionSerializer;
import org.web3j.utils.Numeric;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

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
        byte[] encodedMessage = tx.encode(tx.isSigned());

        writer.putInt(encodedMessage.length);
        writer.putBytes(encodedMessage);
    }

    @Override
    public EthereumTransaction parse(Reader reader) {
        var length = reader.getInt();
        var encodedMessage = reader.getBytes(length);
        return EthereumTransactionDecoder.decode(encodedMessage);
    }
}
