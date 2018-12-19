package com.horizen.box;

import scala.util.Try;

public final class RegularBoxSerializer extends PublicKey25519NoncedBoxSerializer<RegularBox>
{
    @Override
    public Try<RegularBox> parseBytes(byte[] bytes) {
        return super.parseBytes(bytes);
    }

    @Override
    public byte[] toBytes(RegularBox obj) {
        return super.toBytes(obj);
    }
}
