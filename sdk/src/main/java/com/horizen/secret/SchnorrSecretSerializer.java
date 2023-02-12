package com.horizen.secret;

import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte[] secretKey = Checker.readBytes(reader, SchnorrSecret.SECRET_KEY_LENGTH, "secret key");
        byte[] publicKey = Checker.readBytes(reader, SchnorrSecret.PUBLIC_KEY_LENGTH, "public key");

        return new SchnorrSecret(secretKey, publicKey);
    }
}
