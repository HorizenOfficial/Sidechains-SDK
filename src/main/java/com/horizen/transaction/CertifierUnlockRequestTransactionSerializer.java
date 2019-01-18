package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.proposition.Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.HashMap;
import java.util.List;

class CertifierUnlockRequestTransactionSerializer<T extends CertifierUnlockRequestTransaction> implements TransactionSerializer<T>
{
    private ListSerializer<NoncedBox<Proposition>> _boxSerializer;

    CertifierUnlockRequestTransactionSerializer() {
        HashMap<Integer, Serializer<NoncedBox<Proposition>>> supportedBoxSerializers = new HashMap<Integer, Serializer<NoncedBox<Proposition>>>();
        //supportedBoxSerializers.put(1, new RegularBoxSerializer());
        // TO DO: update supported serializers list

        _boxSerializer  = new ListSerializer<NoncedBox<Proposition>>(supportedBoxSerializers);
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
