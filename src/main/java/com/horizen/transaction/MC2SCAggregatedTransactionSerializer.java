package com.horizen.transaction;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

class MC2SCAggregatedTransactionSerializer implements TransactionSerializer<MC2SCAggregatedTransaction>
{
    private ListSerializer<Box<Proposition>> _boxSerializer;

    MC2SCAggregatedTransactionSerializer() {
        HashMap<Integer, Serializer<Box>> supportedBoxSerializers = new HashMap<Integer, Serializer<Box>>();
        //supportedBoxSerializers.put(1, new RegularBoxSerializer());
        //_boxSerializer  = new ListSerializer<Box<Proposition>>(supportedBoxSerializers);
    }

    @Override
    public byte[] toBytes(MC2SCAggregatedTransaction obj) {
        return _boxSerializer.toBytes(obj.newBoxes());
    }

    @Override
    public Try<MC2SCAggregatedTransaction> parseBytes(byte[] bytes) {
        ArrayList<Box<Proposition>> boxes = _boxSerializer.parseBytes(bytes).get();

        // create RegualrTransaction and init with Boxes
        return null;
    }
}

