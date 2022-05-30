package com.horizen.account.transaction;

import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.TransactionEncoder;
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
    public void serialize(EthereumTransaction transaction, Writer writer) {
        var encodedMessage = TransactionEncoder.encode(transaction.getTransaction());
        writer.putInt(encodedMessage.length);
        writer.putBytes(encodedMessage);
    }

    @Override
    public EthereumTransaction parse(Reader reader) {
        var messageLength = reader.getInt();
        var hexMessage = BytesUtils.toHexString(reader.getBytes(messageLength));
        return new EthereumTransaction(TransactionDecoder.decode(hexMessage));
    }
}
