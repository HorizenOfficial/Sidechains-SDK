package com.horizen.sc2sc;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;
import sparkz.core.serialization.SparkzSerializer;

public class CrossChainMessageHashSerializer implements SparkzSerializer<CrossChainMessageHash> {


    @Override
    public void serialize(CrossChainMessageHash s, Writer w) {
        w.putInt(s.bytes().length);
        w.putBytes(s.bytes());
    }

    @Override
    public CrossChainMessageHash parse(Reader reader) {
        return new CrossChainMessageHashImpl(reader.getBytes(reader.getInt()));
    }
}
