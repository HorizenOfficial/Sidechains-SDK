package com.horizen.box;

import sparkz.core.serialization.BytesSerializable;
import sparkz.core.serialization.SparkzSerializer;

public final class ActualKeysSerializer
    implements BytesSerializable
{

    private static ActualKeysSerializer serializer;

    static {
        serializer = new ActualKeysSerializer();
    }

    private ActualKeysSerializer() {
        super();

    }

    @Override
    public byte[] bytes() {
        return BytesSerializable.super.bytes();
    }

    @Override
    public SparkzSerializer<BytesSerializable> serializer() {
        return null;
    }
}
