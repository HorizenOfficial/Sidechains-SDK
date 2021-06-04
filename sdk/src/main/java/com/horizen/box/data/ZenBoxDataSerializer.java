package com.horizen.box.data;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ZenBoxDataSerializer implements NoncedBoxDataSerializer<ZenBoxData> {

    private final static ZenBoxDataSerializer serializer = new ZenBoxDataSerializer();

    private ZenBoxDataSerializer() {
        super();
    }

    public static ZenBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ZenBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public ZenBoxData parse(Reader reader) {
        return ZenBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}