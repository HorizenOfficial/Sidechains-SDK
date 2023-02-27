package com.horizen.utxo.box;

import com.horizen.utxo.box.data.ForgerBoxData;
import com.horizen.utxo.box.data.ForgerBoxDataSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        writer.putLong(box.nonce);
        box.boxData.serializer().serialize(box.boxData, writer);
    }

    @Override
    public ForgerBox parse(Reader reader) {
        Long nonce = reader.getLong();
        ForgerBoxData boxData = ForgerBoxDataSerializer.getSerializer().parse(reader);

        return new ForgerBox(boxData, nonce);
    }
}
