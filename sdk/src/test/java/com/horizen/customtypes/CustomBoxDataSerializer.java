package com.horizen.customtypes;

import com.horizen.box.data.NoncedBoxDataSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class CustomBoxDataSerializer implements NoncedBoxDataSerializer<CustomBoxData> {

    private static CustomBoxDataSerializer serializer;

    static {
        serializer = new CustomBoxDataSerializer();
    }

    private CustomBoxDataSerializer() {
        super();
    }

    public static CustomBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CustomBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public CustomBoxData parse(Reader reader) {
        return CustomBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}