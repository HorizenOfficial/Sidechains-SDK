package com.horizen.box.data;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ForgerBoxDataSerializer implements NoncedBoxDataSerializer<ForgerBoxData> {

    private final static ForgerBoxDataSerializer serializer = new ForgerBoxDataSerializer();

    private ForgerBoxDataSerializer() {
        super();
    }

    public static ForgerBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ForgerBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public ForgerBoxData parse(Reader reader) {
        return ForgerBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}