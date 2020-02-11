package com.horizen.box.data;

import com.horizen.box.NoncedBox;
import com.horizen.proposition.Proposition;
import scorex.core.serialization.BytesSerializable;

public interface BoxData<P extends Proposition, B extends NoncedBox<P>> extends BytesSerializable {

    long value();

    P proposition();

    B getBox(long nonce);

    byte[] customFieldsHash();

    @Override
    byte[] bytes();

    @Override
    BoxDataSerializer serializer();

    byte boxDataTypeId();
}
