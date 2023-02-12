package com.horizen.box.data;

import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;


public final class ZenBoxDataSerializer implements BoxDataSerializer<ZenBoxData> {

    private final static ZenBoxDataSerializer serializer = new ZenBoxDataSerializer();

    private ZenBoxDataSerializer() {
        super();
    }

    public static ZenBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ZenBoxData boxData, Writer writer) {
        boxData.proposition().serializer().serialize(boxData.proposition(), writer);
        writer.putLong(boxData.value());
    }

    @Override
    public ZenBoxData parse(Reader reader) {
        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer().parse(reader);
        long value = Checker.readLongNotLessThanZero(reader, "Value of proposition");
        return new ZenBoxData(proposition, value);
    }
}