package com.horizen.customtypes;

import com.horizen.box.BoxSerializer;
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
        writer.putLong(box.nonce());
        box.getBoxData().serializer().serialize(box.getBoxData(), writer);
    }

    @Override
    public CustomBox parse(Reader reader) {
        long nonce = reader.getLong();
        CustomBoxData boxData = CustomBoxDataSerializer.getSerializer().parse(reader);
        return new CustomBox(boxData, nonce);
    }
}
