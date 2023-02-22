package com.horizen.box;

import com.horizen.box.data.CrossChainRedeemMessageBoxData;
import com.horizen.box.data.CrossChainRedeemMessageBoxDataSerializer;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public final class CrossChainRedeemMessageBoxSerializer implements BoxSerializer<CrossChainRedeemMessageBox> {
    private static final CrossChainRedeemMessageBoxSerializer serializer;

    static {
        serializer = new CrossChainRedeemMessageBoxSerializer();
    }

    private CrossChainRedeemMessageBoxSerializer() {
        super();
    }

    public static CrossChainRedeemMessageBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CrossChainRedeemMessageBox box, Writer writer) {
        writer.putLong(box.nonce);
        box.boxData.serializer().serialize(box.boxData, writer);
    }

    @Override
    public CrossChainRedeemMessageBox parse(Reader reader) {
        long nonce = reader.getLong();
        CrossChainRedeemMessageBoxData boxData = CrossChainRedeemMessageBoxDataSerializer.getSerializer().parse(reader);
        return new CrossChainRedeemMessageBox(boxData, nonce);
    }
}