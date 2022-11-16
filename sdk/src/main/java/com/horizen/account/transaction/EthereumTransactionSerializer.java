package com.horizen.account.transaction;

import com.horizen.account.utils.EthereumTransactionDecoder;
import com.horizen.transaction.TransactionSerializer;
import org.web3j.crypto.*;
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
        byte[] encodedMessage;
        if (tx.isSigned())
            encodedMessage = TransactionEncoder.encode(tx.getTransaction(), tx.getSignatureData());
        else encodedMessage = TransactionEncoder.encode(tx.getTransaction());

        writer.putInt(encodedMessage.length);
        writer.putBytes(encodedMessage);
    }

    @Override
    public EthereumTransaction parse(Reader reader) {
        // TODO: remove reliance on length
        var length = reader.getInt();
        var encodedMessage = reader.getBytes(length);
        var transaction = EthereumTransactionDecoder.decode(Numeric.toHexString(encodedMessage));
        return new EthereumTransaction(transaction);
    }
}