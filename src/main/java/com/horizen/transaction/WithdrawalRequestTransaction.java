package com.horizen.transaction;
/*
import com.horizen.box.NoncedBox;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import com.horizen.serialization.JsonSerializable;
import com.horizen.serialization.JsonSerializer;
import io.circe.Json;

import java.util.ArrayList;
import java.util.List;

public final class WithdrawalRequestTransaction
    extends SidechainTransaction<Proposition, NoncedBox<Proposition>>
    implements JsonSerializable
{
    public static final byte TRANSACTION_TYPE_ID = 4;

    @Override
    public WithdrawalRequestTransactionSerializer serializer() {
        return new WithdrawalRequestTransactionSerializer();
    }

    @Override
    public List<BoxUnlocker<Proposition>> unlockers() { return null; }

    // nothing to create
    @Override
    public List<NoncedBox<Proposition>> newBoxes() {
        return new ArrayList<NoncedBox<Proposition>>();
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
    public boolean transactionSemanticValidity() {
        return false;
    }

    @Override
    public byte transactionTypeId() {
        return TRANSACTION_TYPE_ID; // scorex.core.ModifierTypeId @@ 4.toByte
    }

    @Override
    public byte[] bytes() {
        return null;
    }

    @Override
    public Json toJson() {
        ArrayList<Json> arr = new ArrayList<>();
        scala.collection.mutable.HashMap<String,Json> values = new scala.collection.mutable.HashMap<>();

        values.put("id", Json.fromString(this.id()));
        values.put("fee", Json.fromLong(this.fee()));

        for(NoncedBox<Proposition> b : this.newBoxes())
            arr.add(b.toJson());
        values.put("newBoxes", Json.arr(scala.collection.JavaConverters.collectionAsScalaIterableConverter(arr).asScala().toSeq()));

        return Json.obj(values.toSeq());
    }

    public static WithdrawalRequestTransaction parseJson(Json json) {
        return null;
    }
}
*/