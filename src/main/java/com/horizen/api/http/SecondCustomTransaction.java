package com.horizen.api.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.NoncedBox;
import com.horizen.box.RegularBox;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.secret.PrivateKey25519;
import com.horizen.serialization.Views;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.TransactionJsonSerializer;
import io.circe.Json;
import javafx.util.Pair;
import scorex.core.serialization.BytesSerializable;
import scorex.core.serialization.ScorexSerializer;

import java.util.ArrayList;
import java.util.List;

@JsonView(Views.CustomView.class)
@JsonRootName("secondCustomTransaction")
public class SecondCustomTransaction extends SidechainTransaction<PublicKey25519Proposition, RegularBox> {

    @JsonProperty("regularBox")
    private List<RegularBox> _inputs;
    private List<Pair<PublicKey25519Proposition, Long>> _outputs;
    private List<Signature25519> _signatures;
    private long _fee;
    private long _timestamp;

    @Override
    public boolean transactionSemanticValidity() {
        return true;
    }

    @Override
    public List<BoxUnlocker<PublicKey25519Proposition>> unlockers() {
        return new ArrayList<BoxUnlocker<PublicKey25519Proposition>>();
    }

    @Override
    public List<RegularBox> newBoxes() {
        return new ArrayList<RegularBox>();
    }

    @Override
    public long fee() {
        return _fee;
    }

    @Override
    public long timestamp() {
        return _timestamp;
    }

    @Override
    public byte transactionTypeId() {
        return 0;
    }

    @Override
    public TransactionJsonSerializer jsonSerializer() {
        return null;
    }

    @Override
    public Json toJson() {
        return null;
    }

    @Override
    public ScorexSerializer<BytesSerializable> serializer() {
        return null;
    }

    public SecondCustomTransaction(List<Pair<RegularBox, PrivateKey25519>> from,
                             List<Pair<PublicKey25519Proposition, Long>> to,
                             long fee,
                             long timestamp) {
        List<RegularBox> inputs = new ArrayList<>();
        List<Signature25519> fakeSignatures = new ArrayList<>();
        for(Pair<RegularBox, PrivateKey25519> item : from) {
            inputs.add(item.getKey());
            fakeSignatures.add(null);
        }

        _inputs = inputs;
        _outputs = to;
        _signatures = fakeSignatures;
        _fee = fee;
        _timestamp = timestamp;
    }
}
