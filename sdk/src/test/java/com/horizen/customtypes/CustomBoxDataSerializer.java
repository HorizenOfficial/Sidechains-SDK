package com.horizen.customtypes;

import com.horizen.box.data.BoxDataSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class CustomBoxDataSerializer implements BoxDataSerializer<CustomBoxData> {

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
        boxData.proposition().serializer().serialize(boxData.proposition(), writer);
        writer.putLong(boxData.value());
    }

    @Override
    public CustomBoxData parse(Reader reader) {
        CustomPublicKeyProposition proposition = CustomPublicKeyPropositionSerializer.getSerializer().parse(reader);
        long value = reader.getLong();
        return new CustomBoxData(proposition, value);
    }
}