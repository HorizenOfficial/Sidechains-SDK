package com.horizen.customtypes;

import com.horizen.box.BoxSerializer;
import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class CustomBoxSerializer implements BoxSerializer<CustomBox>
{
    private static CustomBoxSerializer serializer;

    static {
        serializer = new CustomBoxSerializer();
    }

    private CustomBoxSerializer() {
        super();

    }

    public static CustomBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CustomBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public CustomBox parse(Reader reader) {
        return CustomBox.parseBytes(reader.getBytes(reader.remaining()));
    }
}
