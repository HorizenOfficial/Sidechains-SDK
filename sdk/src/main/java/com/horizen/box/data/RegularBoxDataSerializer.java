package com.horizen.box.data;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class RegularBoxDataSerializer implements NoncedBoxDataSerializer<RegularBoxData> {

    private final static RegularBoxDataSerializer serializer = new RegularBoxDataSerializer();

    private RegularBoxDataSerializer() {
        super();
    }

    public static RegularBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(RegularBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public RegularBoxData parse(Reader reader) {
        return RegularBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}