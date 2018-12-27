package com.horizen.transaction;

import com.horizen.box.RegularBox;
import com.horizen.box.RegularBoxSerializer;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

class RegularTransactionSerializer implements TransactionSerializer<RegularTransaction>
{
    private ListSerializer<RegularBox> _boxSerializer;
    // todo: keep another serializers for inputs and signatures(secrets)

    RegularTransactionSerializer() {
        HashMap<Integer, Serializer<RegularBox>> supportedBoxSerializers = new HashMap<Integer, Serializer<RegularBox>>();
        supportedBoxSerializers.put(1, new RegularBoxSerializer());

        _boxSerializer  = new ListSerializer<RegularBox>(supportedBoxSerializers);
    }

    @Override
    public byte[] toBytes(RegularTransaction obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<RegularTransaction> parseBytes(byte[] bytes) {
        ArrayList<RegularBox> boxes = _boxSerializer.parseBytes(bytes).get();

        // create RegualrTransaction and init with Boxes
        return null;
    }
}

