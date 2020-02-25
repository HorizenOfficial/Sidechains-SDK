package com.horizen.box.data;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class CertifierRightBoxDataSerializer implements NoncedBoxDataSerializer<CertifierRightBoxData> {

    private final static CertifierRightBoxDataSerializer serializer = new CertifierRightBoxDataSerializer();

    private CertifierRightBoxDataSerializer() {
        super();
    }

    public static CertifierRightBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CertifierRightBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public CertifierRightBoxData parse(Reader reader) {
        return CertifierRightBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}