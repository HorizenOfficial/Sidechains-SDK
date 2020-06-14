package com.horizen.box;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ForgerBoxSerializer implements BoxSerializer<ForgerBox> {

    private static ForgerBoxSerializer serializer;

    static {
        serializer = new ForgerBoxSerializer();
    }

    private ForgerBoxSerializer() {
        super();
    }

    public static ForgerBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ForgerBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public ForgerBox parse(Reader reader) {
        return ForgerBox.parseBytes(reader.getBytes(reader.remaining()));
    }
}
