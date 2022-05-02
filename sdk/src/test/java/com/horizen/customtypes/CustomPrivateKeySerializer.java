package com.horizen.customtypes;

import com.horizen.secret.SecretSerializer;
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
        writer.putBytes(secret.privateKeyBytes);
        writer.putBytes(secret.publicKeyBytes);
    }

    @Override
    public CustomPrivateKey parse(Reader reader) {
        byte[] privateKeyBytes = reader.getBytes(CustomPrivateKey.PRIVATE_KEY_LENGTH);
        byte[] publicKeyBytes = reader.getBytes(CustomPrivateKey.PUBLIC_KEY_LENGTH);
        return new CustomPrivateKey(privateKeyBytes, publicKeyBytes);
    }
}
