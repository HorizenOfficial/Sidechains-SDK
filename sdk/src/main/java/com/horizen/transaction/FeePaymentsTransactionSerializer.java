package com.horizen.transaction;

import com.horizen.box.data.ZenBoxDataSerializer;
import com.horizen.utils.ListSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;
import com.horizen.box.data.ZenBoxData;

import java.util.List;

class FeePaymentsTransactionSerializer implements TransactionSerializer<FeePaymentsTransaction>
{
    private static FeePaymentsTransactionSerializer serializer;

    private static ListSerializer<ZenBoxData> outputsSerializer = new ListSerializer(ZenBoxDataSerializer.getSerializer());

    static {
        serializer = new FeePaymentsTransactionSerializer();
    }

    private FeePaymentsTransactionSerializer() {
        super();
    }

    public static FeePaymentsTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(FeePaymentsTransaction transaction, Writer writer) {
        writer.put(transaction.version());
        outputsSerializer.serialize(transaction.getOutputData(), writer);
    }

    @Override
    public FeePaymentsTransaction parse(Reader reader) {
        byte version = reader.getByte();
        List<ZenBoxData> outputList = outputsSerializer.parse(reader);

        return new FeePaymentsTransaction(outputList, version);
    }
}
