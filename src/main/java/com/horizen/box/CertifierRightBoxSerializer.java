package com.horizen.box;

import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class CertifierRightBoxSerializer implements BoxSerializer<CertifierRightBox>
{

    private static CertifierRightBoxSerializer serializer;

    static {
        serializer = new CertifierRightBoxSerializer();
    }

    private CertifierRightBoxSerializer() {
        super();
    }

    public static CertifierRightBoxSerializer getSerializer() {
        return serializer;
    }

    /*
    @Override
    public byte[] toBytes(CertifierRightBox box) {
        return box.bytes();
    }

    @Override
    public Try<CertifierRightBox> parseBytesTry(byte[] bytes) {
        return CertifierRightBox.parseBytes(bytes);
    }
    */

    @Override
    public void serialize(CertifierRightBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public CertifierRightBox parse(Reader reader) {
        return CertifierRightBox.parseBytes(reader.getBytes(reader.remaining())).get();
    }
}
