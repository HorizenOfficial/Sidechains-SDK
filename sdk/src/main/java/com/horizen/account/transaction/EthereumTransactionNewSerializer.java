package com.horizen.account.transaction;

import com.horizen.account.utils.EthereumTransactionNewDecoder;
import com.horizen.transaction.TransactionSerializer;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class EthereumTransactionNewSerializer implements TransactionSerializer<EthereumTransactionNew> {

    private static final EthereumTransactionNewSerializer serializer;

    static {
        serializer = new EthereumTransactionNewSerializer();
    }

    private EthereumTransactionNewSerializer() {
        super();
    }

    public static EthereumTransactionNewSerializer getSerializer() {
        return serializer;
    }

    // Maybe we need to do the serialization in a different way to be eth compatible,
    // because of here used message length integer needed for decoding
    @Override
    public void serialize(EthereumTransactionNew tx, Writer writer) {
        byte[] encodedMessage = tx.encode(tx.getSignatureData());

        writer.putInt(encodedMessage.length);
        writer.putBytes(encodedMessage);
    }

    @Override
    public EthereumTransactionNew parse(Reader reader) {
        var length = reader.getInt();
        var encodedMessage = reader.getBytes(length);
        return EthereumTransactionNewDecoder.decode(Numeric.toHexString(encodedMessage));
    }
}
