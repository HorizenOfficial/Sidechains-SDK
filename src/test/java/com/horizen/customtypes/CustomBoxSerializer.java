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
    public byte[] toBytes(CustomBox box) {
        return box.bytes();
    }

    @Override
    public Try<CustomBox> parseBytesTry(byte[] bytes) {
        return CustomBox.parseBytes(bytes);
    }

    @Override
    public void serialize(CustomBox obj, Writer writer) {

    }

    @Override
    public CustomBox parse(Reader reader) {
        return null;
    }
}
