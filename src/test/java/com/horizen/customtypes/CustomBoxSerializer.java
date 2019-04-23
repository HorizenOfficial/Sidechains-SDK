package com.horizen.customtypes;

import com.horizen.box.BoxSerializer;
import scala.util.Try;

public class CustomBoxSerializer implements BoxSerializer<CustomBox>
{
    private static CustomBoxSerializer serializer;

    static {
        serializer = new CustomBoxSerializer();
    }

    private CustomBoxSerializer() {
        super();

    }

    public static CustomBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public byte[] toBytes(CustomBox box) {
        return box.bytes();
    }

    @Override
    public Try<CustomBox> parseBytes(byte[] bytes) {
        return CustomBox.parseBytes(bytes);
    }

}
