package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;

public final class RegularBoxSerializer implements BoxSerializer<RegularBox>
{

    private static RegularBoxSerializer serializer;

    static {
        serializer = new RegularBoxSerializer();
    }

    private RegularBoxSerializer() {
        super();

    }

    public static RegularBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public byte[] toBytes(RegularBox box) {
        return box.bytes();
    }

    @Override
    public Try<RegularBox> parseBytes(byte[] bytes) {
        return RegularBox.parseBytes(bytes);
    }
}
