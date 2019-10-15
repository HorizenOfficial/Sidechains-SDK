package com.horizen.box;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class RegularBoxSerializer
    implements BoxSerializer<RegularBox>
{

    private static RegularBoxSerializer serializer;

    static {
        serializer = new RegularBoxSerializer();
    }

    private RegularBoxSerializer() {
        super();

    }

    public static RegularBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(RegularBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public RegularBox parse(Reader reader) {
        return RegularBox.parseBytes(reader.getBytes(reader.remaining()));
    }

}
