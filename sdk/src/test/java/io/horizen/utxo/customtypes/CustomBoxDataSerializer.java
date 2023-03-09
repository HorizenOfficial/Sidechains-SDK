package io.horizen.utxo.customtypes;

import io.horizen.customtypes.CustomPublicKeyProposition;
import io.horizen.customtypes.CustomPublicKeyPropositionSerializer;
import io.horizen.utxo.box.data.BoxDataSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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