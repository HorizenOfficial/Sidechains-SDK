package io.horizen.utxo.box.data;

import io.horizen.proposition.Proposition;
import io.horizen.utxo.box.Box;
import sparkz.core.serialization.BytesSerializable;

public interface BoxData<P extends Proposition, B extends Box<P>> extends BytesSerializable {

    long value();

    P proposition();

    B getBox(long nonce);

    byte[] customFieldsHash();

    @Override
    default byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    BoxDataSerializer serializer();
}
