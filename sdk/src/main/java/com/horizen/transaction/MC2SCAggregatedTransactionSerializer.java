package com.horizen.transaction;

import com.horizen.transaction.mainchain.BwtRequestSerializer;
import com.horizen.transaction.mainchain.ForwardTransferSerializer;
import com.horizen.transaction.mainchain.SidechainCreationSerializer;
import com.horizen.transaction.mainchain.SidechainRelatedMainchainOutput;
import com.horizen.utils.Checker;
import com.horizen.utils.DynamicTypedSerializer;
import com.horizen.utils.ListSerializer;
import sparkz.core.serialization.SparkzSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte version = Checker.version(reader, MC2SC_AGGREGATED_TRANSACTION_VERSION, "MC2SC Aggregated Transaction");
        List<SidechainRelatedMainchainOutput> outputs = mc2scTransactionsSerializer.parse(reader);
        Checker.bufferShouldBeEmpty(reader.remaining());
        return new MC2SCAggregatedTransaction(outputs, version);
    }
}

