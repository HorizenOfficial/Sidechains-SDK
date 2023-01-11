package com.horizen.sc2sc;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;
import sparkz.core.serialization.BytesSerializable;
import sparkz.core.serialization.SparkzSerializer;

public class CrossChainMessageHashSerializer<T extends  CrossChainMessageHash> implements SparkzSerializer<T> {

    private static CrossChainMessageHashSerializer serializer;

    static {
        serializer = new CrossChainMessageHashSerializer();
    }

    @Override
    public void serialize(T s, Writer w) {
        w.putInt(s.bytes().length);
        w.putBytes(s.bytes());
    }

    @Override
    public T parse(Reader reader) {
        return (T)new CrossChainMessageHashImpl(reader.getBytes(reader.getInt()));
    }

    public static SparkzSerializer<BytesSerializable> getSerializer(){
        return serializer;
    }
}