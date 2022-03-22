package com.horizen.box;

import com.horizen.box.data.ForgerBoxData;
import com.horizen.box.data.ForgerBoxDataSerializer;
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
