package com.horizen.secret;

import com.horizen.cryptolibprovider.CryptoLibProvider;
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
        writer.putBytes(secret.bytes());
    }

    @Override
    public SchnorrSecret parse(Reader reader) {
        return SchnorrSecret.parse(reader.getBytes(reader.remaining()));
    }
}
