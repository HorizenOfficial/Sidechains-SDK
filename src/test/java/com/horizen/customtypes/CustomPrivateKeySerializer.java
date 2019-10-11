package com.horizen.customtypes;

import com.horizen.secret.SecretSerializer;
import scala.util.Try;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class CustomPrivateKeySerializer implements SecretSerializer<CustomPrivateKey> 
{
    private static CustomPrivateKeySerializer serializer;

    static {
        serializer = new CustomPrivateKeySerializer();
    }

    private CustomPrivateKeySerializer() {
        super();

    }

    public static CustomPrivateKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CustomPrivateKey secret, Writer writer) {
        writer.putBytes(secret.bytes());
    }

    @Override
    public CustomPrivateKey parse(Reader reader) {
        return CustomPrivateKey.parseBytes(reader.getBytes(reader.remaining()));
    }
}
