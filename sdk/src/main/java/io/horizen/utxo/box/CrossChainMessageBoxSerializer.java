package io.horizen.utxo.box;

import io.horizen.utxo.box.data.CrossChainMessageBoxData;
import io.horizen.utxo.box.data.CrossChainMessageBoxDataSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public final class CrossChainMessageBoxSerializer implements BoxSerializer<CrossChainMessageBox> {

    private static final CrossChainMessageBoxSerializer serializer;

    static {
        serializer = new CrossChainMessageBoxSerializer();
    }

    private CrossChainMessageBoxSerializer() {
        super();
    }

    public static CrossChainMessageBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CrossChainMessageBox box, Writer writer) {
        writer.putLong(box.nonce);
        box.boxData.serializer().serialize(box.boxData, writer);
    }

    @Override
    public CrossChainMessageBox parse(Reader reader) {
        long nonce = reader.getLong();
        CrossChainMessageBoxData boxData = CrossChainMessageBoxDataSerializer.getSerializer().parse(reader);
        return new CrossChainMessageBox(boxData, nonce);
    }
}
