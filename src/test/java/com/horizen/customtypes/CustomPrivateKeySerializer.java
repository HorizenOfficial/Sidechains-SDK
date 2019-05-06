package com.horizen.customtypes;

import com.horizen.secret.SecretSerializer;
import scala.util.Try;

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
    public Try<CustomPrivateKey> parseBytes(byte[] bytes) {
        return CustomPrivateKey.parseBytes(bytes);
    }
    
}
