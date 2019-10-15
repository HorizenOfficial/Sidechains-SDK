package com.horizen.transaction;
/*
import com.horizen.box.NoncedBox;
import com.horizen.proposition.Proposition;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.DynamicTypedSerializer;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.HashMap;
import java.util.List;

public class WithdrawalRequestTransactionSerializer<T extends WithdrawalRequestTransaction> implements TransactionSerializer<T>
{
    private ListSerializer<NoncedBox<Proposition>> _boxSerializer;

    WithdrawalRequestTransactionSerializer() {
        DynamicTypedSerializer<NoncedBox<Proposition>, ScorexSerializer<? extends NoncedBox<Proposition>>> supportedBoxCompanion =
                new DynamicTypedSerializer<>(
                        new HashMap<Byte, ScorexSerializer<? extends NoncedBox<Proposition>>>() {{
                            // put(RegularBox.BOX_TYPE_ID, RegularBoxSerializer.getSerializer())
                            // TO DO: update supported serializers list
                        }}, new HashMap<>());

        _boxSerializer  = new ListSerializer<NoncedBox<Proposition>>(supportedBoxCompanion);
    }

    @Override
    public void serialize(T transaction, Writer writer) {
        writer.putBytes(_boxSerializer.toBytes(transaction.newBoxes()));
    }

    @Override
    public T parse(Reader reader) {
        List<NoncedBox<Proposition>> boxes = _boxSerializer.parseBytesTry(reader.getBytes(reader.remaining())).get();
        return null;
    }

}
*/