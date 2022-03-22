package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.box.ZenBox;
import com.horizen.box.ZenBoxSerializer;
import com.horizen.box.data.*;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.Proposition;
import com.horizen.utils.DynamicTypedSerializer;
import com.horizen.utils.ListSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.HashMap;
import java.util.List;

import static com.horizen.box.CoreBoxesIdsEnum.*;
import static com.horizen.transaction.BoxTransaction.MAX_TRANSACTION_NEW_BOXES;
import static com.horizen.transaction.BoxTransaction.MAX_TRANSACTION_UNLOCKERS;


public final class RegularTransactionSerializer implements TransactionSerializer<RegularTransaction>
{
    private static RegularTransactionSerializer serializer;

    static {
        serializer = new RegularTransactionSerializer();
    }

    // Serializers definition
    private static ListSerializer<ZenBox> boxListSerializer =
            new ListSerializer<>(ZenBoxSerializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);
    private static ListSerializer<BoxData<? extends Proposition, ? extends Box<? extends Proposition>>> boxDataListSerializer =
            new ListSerializer<>(new DynamicTypedSerializer<>(
                    new HashMap<Byte, BoxDataSerializer>() {{
                        put(ZenBoxId.id(), ZenBoxDataSerializer.getSerializer());
                        put(WithdrawalRequestBoxId.id(), WithdrawalRequestBoxDataSerializer.getSerializer());
                        put(ForgerBoxId.id(), ForgerBoxDataSerializer.getSerializer());
                    }}, new HashMap<>()
            ), MAX_TRANSACTION_NEW_BOXES);
    private static ListSerializer<Signature25519> signaturesSerializer =
            new ListSerializer<>(Signature25519Serializer.getSerializer(), MAX_TRANSACTION_UNLOCKERS);

    private RegularTransactionSerializer() {
        super();
    }

    public static RegularTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(RegularTransaction transaction, Writer writer) {
        writer.putLong(transaction.fee());
        boxListSerializer.serialize(transaction.inputs, writer);
        boxDataListSerializer.serialize(transaction.outputs, writer);
        signaturesSerializer.serialize(transaction.signatures, writer);
    }

    @Override
    public RegularTransaction parse(Reader reader) {
        long fee = reader.getLong();
        List<ZenBox> inputs = boxListSerializer.parse(reader);
        List<BoxData<? extends Proposition, ? extends Box<? extends Proposition>>> outputs = boxDataListSerializer.parse(reader);
        List<Signature25519> signatures = signaturesSerializer.parse(reader);

        return new RegularTransaction(inputs, outputs, signatures, fee);
    }
}

