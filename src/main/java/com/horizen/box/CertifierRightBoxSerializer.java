package com.horizen.box;

import scala.util.Try;

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

    @Override
    public byte[] toBytes(CertifierRightBox box) {
        return box.bytes();
    }

    @Override
    public Try<CertifierRightBox> parseBytes(byte[] bytes) {
        return CertifierRightBox.parseBytes(bytes);
    }
}
