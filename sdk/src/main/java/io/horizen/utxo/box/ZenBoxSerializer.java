package io.horizen.utxo.box;

import com.horizen.utxo.box.data.ZenBoxData;
import com.horizen.utxo.box.data.ZenBoxDataSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        writer.putLong(box.nonce);
        box.boxData.serializer().serialize(box.boxData, writer);
    }

    @Override
    public ZenBox parse(Reader reader) {
        Long nonce = reader.getLong();
        ZenBoxData boxData = ZenBoxDataSerializer.getSerializer().parse(reader);

        return new ZenBox(boxData, nonce);
    }
}
