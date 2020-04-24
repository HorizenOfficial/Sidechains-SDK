package com.horizen.secret;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class SchnorrSecretKeySerializer implements SecretSerializer<SchnorrSecretKey> {
    private static SchnorrSecretKeySerializer serializer;

    static {
        serializer = new SchnorrSecretKeySerializer();
    }

    private SchnorrSecretKeySerializer() {
        super();
    }

    public static SchnorrSecretKeySerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SchnorrSecretKey secret, Writer writer) {
        writer.putBytes(secret.bytes());
    }

    @Override
    public SchnorrSecretKey parse(Reader reader) {
        return SchnorrSecretKey.parse(reader.getBytes(reader.remaining()));
    }
}
