package com.horizen.transaction.serializers;

import com.horizen.transaction.MC2SCAggregatedTransaction;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.transaction.mainchain.BwtRequestSerializer;
import com.horizen.transaction.mainchain.ForwardTransferSerializer;
import com.horizen.transaction.mainchain.SidechainCreationSerializer;
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput;
import com.horizen.utils.DynamicTypedSerializer;
import com.horizen.utils.ListSerializer;
import sparkz.core.serialization.SparkzSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.HashMap;
import java.util.List;

import static com.horizen.transaction.MC2SCAggregatedTransaction.MC2SC_AGGREGATED_TRANSACTION_VERSION;

public final class MC2SCAggregatedTransactionSerializer implements TransactionSerializer<MC2SCAggregatedTransaction> {
    private static MC2SCAggregatedTransactionSerializer serializer;

    // Serializers definition
    private static ListSerializer<SidechainRelatedMainchainOutput> mc2scTransactionsSerializer = new ListSerializer<>(
            new DynamicTypedSerializer<>(
                    new HashMap<Byte, SparkzSerializer<SidechainRelatedMainchainOutput>>() {{
                        put((byte)1, (SparkzSerializer) SidechainCreationSerializer.getSerializer());
                        put((byte)2, (SparkzSerializer) ForwardTransferSerializer.getSerializer());
                        put((byte)3, (SparkzSerializer) BwtRequestSerializer.getSerializer());
                    }}, new HashMap<>()
            ));

    static {
        serializer = new MC2SCAggregatedTransactionSerializer();
    }

    private MC2SCAggregatedTransactionSerializer() {
        super();
    }

    public static MC2SCAggregatedTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(MC2SCAggregatedTransaction transaction, Writer writer) {
        writer.put(transaction.version());
        mc2scTransactionsSerializer.serialize(transaction.mc2scTransactionsOutputs(), writer);
    }

    @Override
    public MC2SCAggregatedTransaction parse(Reader reader) {
        byte version = reader.getByte();

        if (version != MC2SC_AGGREGATED_TRANSACTION_VERSION) {
            throw new IllegalArgumentException(String.format("Unsupported transaction version[%d].", version));
        }

        List<SidechainRelatedMainchainOutput> outputs = mc2scTransactionsSerializer.parse(reader);

        return new MC2SCAggregatedTransaction(outputs, version);
    }
}
