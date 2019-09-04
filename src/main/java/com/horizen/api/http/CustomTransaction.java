package com.horizen.api.http;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@JsonView(Views.Default.class)
@JsonInclude
@JsonRootName("customTransaction")
public final class CustomTransaction extends SidechainTransaction<PublicKey25519Proposition, RegularBox> {

    private List<RegularBox> _inputs;
    private List<Pair<PublicKey25519Proposition, Long>> _outputs;
    private List<Signature25519> _signatures;
    private long _fee;
    private long _timestamp;

    @JsonProperty("messageToSign")
    @Override
    public byte[] messageToSign() {
        return "message to sign".getBytes();
    }

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

    @JsonProperty("myFee")
    @Override
    public long fee() {
        return _fee;
    }

    @JsonView(Views.CustomView.class)
    @JsonSerialize(using = CustomTransactionJsonSerializer.class)
    @JsonProperty("myTimestamp")
    @Override
    public long timestamp() {
        return _timestamp;
    }

    @Override
    public byte transactionTypeId() {
        return 9;
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

    public CustomTransaction(List<Pair<RegularBox, PrivateKey25519>> from,
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
