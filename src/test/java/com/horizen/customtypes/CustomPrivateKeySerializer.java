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
    public byte[] toBytes(CustomPrivateKey secret) {
        return secret.bytes();
    }

    @Override
    public Try<CustomPrivateKey> parseBytesTry(byte[] bytes) {
        return CustomPrivateKey.parseBytes(bytes);
    }

    @Override
    public void serialize(CustomPrivateKey obj, Writer writer) {

    }

    @Override
    public CustomPrivateKey parse(Reader reader) {
        return null;
    }
}
