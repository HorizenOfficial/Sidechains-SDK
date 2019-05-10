package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.box.RegularBox;
import com.horizen.box.RegularBoxSerializer;
import com.horizen.proposition.Proposition;
import com.horizen.utils.ListSerializer;
import com.horizen.utils.SerializableCompanion;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.HashMap;
import java.util.List;

public class WithdrawalRequestTransactionSerializer<T extends WithdrawalRequestTransaction> implements TransactionSerializer<T>
{
    private ListSerializer<NoncedBox<Proposition>> _boxSerializer;

    WithdrawalRequestTransactionSerializer() {
        SerializableCompanion<NoncedBox<Proposition>, Serializer<? extends NoncedBox<Proposition>>> supportedBoxCompanion =
                new SerializableCompanion<>(
                        new HashMap<Byte, Serializer<? extends NoncedBox<Proposition>>>() {{
                            // put(RegularBox.BOX_TYPE_ID, RegularBoxSerializer.getSerializer())
                            // TO DO: update supported serializers list
                        }}, new HashMap<>());

        _boxSerializer  = new ListSerializer<NoncedBox<Proposition>>(supportedBoxCompanion);
    }

    @Override
    public byte[] toBytes(T obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<T> parseBytes(byte[] bytes) {
        List<NoncedBox<Proposition>> boxes = _boxSerializer.parseBytes(bytes).get();
        return null;
    }
}
