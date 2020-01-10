package com.horizen.box.data;

import com.horizen.proposition.Proposition;
import scorex.core.serialization.BytesSerializable;

public interface BoxData<P extends Proposition> extends BytesSerializable {

    long value();

    P proposition();

    @Override
    byte[] bytes();

    @Override
    BoxDataSerializer serializer();
}
