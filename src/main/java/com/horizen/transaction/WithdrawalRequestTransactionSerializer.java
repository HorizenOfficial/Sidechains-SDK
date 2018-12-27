package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.proposition.Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

class WithdrawalRequestTransactionSerializer implements TransactionSerializer<WithdrawalRequestTransaction>
{
    private ListSerializer<NoncedBox<Proposition>> _boxSerializer;

    WithdrawalRequestTransactionSerializer() {
        HashMap<Integer, Serializer<NoncedBox<Proposition>>> supportedBoxSerializers = new HashMap<Integer, Serializer<NoncedBox<Proposition>>>();
        //supportedBoxSerializers.put(1, new RegularBoxSerializer());
        // TO DO: update supported serializers list

        _boxSerializer  = new ListSerializer<NoncedBox<Proposition>>(supportedBoxSerializers);
    }

    @Override
    public byte[] toBytes(WithdrawalRequestTransaction obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<WithdrawalRequestTransaction> parseBytes(byte[] bytes) {
        ArrayList<NoncedBox<Proposition>> boxes = _boxSerializer.parseBytes(bytes).get();
        return null;
    }
}
