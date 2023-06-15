package io.horizen.utxo.transaction;

import io.horizen.transaction.TransactionSerializer;
import io.horizen.utils.ListSerializer;
import io.horizen.utxo.box.ZenBox;
import io.horizen.utxo.box.ZenBoxSerializer;
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
        byte version = reader.getByte();
        List<ZenBox> feePayments = outputsSerializer.parse(reader);

        return new FeePaymentsTransaction(feePayments, version);
    }
}
