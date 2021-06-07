package com.horizen.box;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ZenBoxSerializer
    implements BoxSerializer<ZenBox>
{

    private static ZenBoxSerializer serializer;

    static {
        serializer = new ZenBoxSerializer();
    }

    private ZenBoxSerializer() {
        super();

    }

    public static ZenBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ZenBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public ZenBox parse(Reader reader) {
        return ZenBox.parseBytes(reader.getBytes(reader.remaining()));
    }

}
