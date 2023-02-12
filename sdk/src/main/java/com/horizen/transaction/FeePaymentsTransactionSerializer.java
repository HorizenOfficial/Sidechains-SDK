package com.horizen.transaction;

import com.horizen.box.ZenBox;
import com.horizen.box.ZenBoxSerializer;
import com.horizen.utils.Checker;
import com.horizen.utils.ListSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

import java.util.List;

public class FeePaymentsTransactionSerializer implements TransactionSerializer<FeePaymentsTransaction>
{
    private static FeePaymentsTransactionSerializer serializer;

    private static ListSerializer<ZenBox> outputsSerializer = new ListSerializer(ZenBoxSerializer.getSerializer());

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
        outputsSerializer.serialize(transaction.newBoxes(), writer);
    }

    @Override
    public FeePaymentsTransaction parse(Reader reader) {
        byte version = Checker.readByte(reader, "version");
        List<ZenBox> feePayments = outputsSerializer.parse(reader);

        Checker.bufferShouldBeEmpty(reader.remaining());
        return new FeePaymentsTransaction(feePayments, version);
    }
}
