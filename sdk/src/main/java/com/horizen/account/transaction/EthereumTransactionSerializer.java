package com.horizen.account.transaction;

import com.horizen.transaction.TransactionSerializer;
import org.web3j.crypto.*;
import org.web3j.rlp.*;
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
    public void serialize(EthereumTransaction transaction, Writer writer) {
        byte[] encodedMessage;

        if (transaction.getSignature() != null) {
            Sign.SignatureData signatureData = new Sign.SignatureData(transaction.getSignature().getV(), transaction.getSignature().getR(), transaction.getSignature().getS());
            var rlpValues = TransactionEncoder.asRlpValues(transaction.getTransaction(), signatureData);
            RlpList rlpList = new RlpList(rlpValues);
            encodedMessage = RlpEncoder.encode(rlpList);
        } else {
            if (transaction.getTransaction().isEIP1559Transaction())
                // TODO: get chain ID and put in
                // chain id is used only in the creation of the signature data, but not contained in the transaction itself. 
                // need a way to get the chain id on verification of signatures and here
                encodedMessage = TransactionEncoder.encode(transaction.getTransaction()/*, chainId*/); 
            else 
                encodedMessage = TransactionEncoder.encode(transaction.getTransaction());
        }

        writer.putInt(encodedMessage.length);
        writer.putBytes(encodedMessage);
    }

    @Override
    public EthereumTransaction parse(Reader reader) {
        var length = reader.getInt();
        var encodedMessage = reader.getBytes(length);
        var transaction = TransactionDecoder.decode(Numeric.toHexString(encodedMessage));

        if (transaction instanceof SignedRawTransaction)
            return new EthereumTransaction((SignedRawTransaction) transaction);
        return new EthereumTransaction(transaction);
    }
}
