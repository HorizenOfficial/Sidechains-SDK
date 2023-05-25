package io.horizen.sc2sc;

import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;
import sparkz.core.serialization.SparkzSerializer;

public class CrossChainMessageHashSerializer<T extends CrossChainMessageHash> implements SparkzSerializer<T> {

    private static final CrossChainMessageHashSerializer serializer;

    static {
        serializer = new CrossChainMessageHashSerializer();
    }

    @Override
    public void serialize(T s, Writer w) {
        w.putInt(s.getValue().length);
        w.putBytes(s.getValue());
    }

    @Override
    public T parse(Reader reader) {
        return (T)new CrossChainMessageHash(reader.getBytes(reader.getInt()));
    }

    public static CrossChainMessageHashSerializer<CrossChainMessageHash> getSerializer(){
        return serializer;
    }
}
