package com.horizen.box;

import scala.util.Try;

public final class RegularBoxSerializer<B extends RegularBox> extends PublicKey25519NoncedBoxSerializer<B>
{
    @Override
    public Try<B> parseBytes(byte[] bytes) {
        return super.parseBytes(bytes);
    }

    @Override
    public byte[] toBytes(B obj) {
        return super.toBytes(obj);
    }
}
