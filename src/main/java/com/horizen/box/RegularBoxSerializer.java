package com.horizen.box;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.serialization.JsonSerializer;
import io.circe.Json;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.Arrays;

public final class RegularBoxSerializer
    implements BoxSerializer<RegularBox>
    , JsonSerializer<RegularBox>
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

    /*
    @Override
    public byte[] toBytes(RegularBox box) {
        return box.bytes();
    }

    @Override
    public Try<RegularBox> parseBytesTry(byte[] bytes) {
        return RegularBox.parseBytes(bytes);
    }
    */

    @Override
    public void serialize(RegularBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public RegularBox parse(Reader reader) {
        return RegularBox.parseBytes(reader.getBytes(reader.remaining())).get();
    }

    @Override
    public RegularBox parseJson(Json json) {
        return RegularBox.parseJson(json);
    }
}
