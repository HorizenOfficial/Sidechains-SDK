package com.horizen.secret;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public class SchnorrSecretSerializer implements SecretSerializer<SchnorrSecret> {
    private static SchnorrSecretSerializer serializer;

    static {
        serializer = new SchnorrSecretSerializer();
    }

    private SchnorrSecretSerializer() {
        super();
    }

    public static SchnorrSecretSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SchnorrSecret secret, Writer writer) {
        writer.putBytes(secret.secretBytes);
        writer.putBytes(secret.publicBytes);
    }

    @Override
    public SchnorrSecret parse(Reader reader) {
        byte[] secretKey = reader.getBytes(SchnorrSecret.SECRET_KEY_LENGTH);
        byte[] publicKey = reader.getBytes(SchnorrSecret.PUBLIC_KEY_LENGTH);

        return new SchnorrSecret(secretKey, publicKey);
    }
}
