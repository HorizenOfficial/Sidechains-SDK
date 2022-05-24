package com.horizen.account.transaction;

import com.horizen.account.proof.SignatureSecp256k1Serializer;
import com.horizen.account.proposition.PublicKeySecp256k1PropositionSerializer;
import com.horizen.transaction.TransactionSerializer;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;


public class EthereumTransactionSerializer implements TransactionSerializer<EthereumTransaction> {

    private static final EthereumTransactionSerializer serializer;
    private static final SignatureSecp256k1Serializer signatureSerializer;
    private static final PublicKeySecp256k1PropositionSerializer propositionSerializer;

    static {
        serializer = new EthereumTransactionSerializer();
        signatureSerializer = SignatureSecp256k1Serializer.getSerializer();
        propositionSerializer = PublicKeySecp256k1PropositionSerializer.getSerializer();
    }

    private EthereumTransactionSerializer() {
        super();
    }

    public static EthereumTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(EthereumTransaction transaction, Writer writer) {
        var encodedMessage = TransactionEncoder.encode(transaction.getTransaction());
        writer.putInt(encodedMessage.length);
        writer.putBytes(encodedMessage);
        signatureSerializer.serialize(transaction.getSignature(), writer);
        propositionSerializer.serialize(transaction.getFrom(), writer);
    }

    @Override
    public EthereumTransaction parse(Reader reader) {
        var messageLength = reader.getInt();
        var hexMessage = Numeric.toHexString(reader.getBytes(messageLength));
        var signature = signatureSerializer.parse(reader);
        var proposition = propositionSerializer.parse(reader);
        return new EthereumTransaction(TransactionDecoder.decode(hexMessage), signature, proposition);
    }
}
