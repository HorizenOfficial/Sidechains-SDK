package com.horizen.transaction;

import com.horizen.box.BoxUnlocker;
import com.horizen.box.RegularBox;
import com.horizen.box.RegularBoxSerializer;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Try;
import scorex.core.serialization.Serializer;

import java.util.ArrayList;
import java.util.HashMap;

public final class RegularTransaction extends NoncedBoxTransaction<PublicKey25519Proposition, RegularBox>
{

    @Override
    public RegularTransactionSerializer serializer() {
        return new RegularTransactionSerializer();
    }

    @Override
    public ArrayList<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return null;
    }

    @Override
    public ArrayList<RegularBox> newBoxes() {
        return null;
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return 0;
    }

    @Override
    public scorex.core.ModifierTypeId transactionTypeId() {
        return null;// scorex.core.ModifierTypeId @@ 1.toByte();
    }
}

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
