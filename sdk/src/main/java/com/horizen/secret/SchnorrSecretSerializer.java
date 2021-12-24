package com.horizen.secret;

import com.google.common.primitives.Ints;
import com.horizen.cryptolibprovider.CryptoLibProvider;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.Arrays;

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
        writer.putInt(secret.secretBytes.length);
        writer.putBytes(secret.secretBytes);
        writer.putInt(secret.publicBytes.length);
        writer.putBytes(secret.publicBytes);
    }

    @Override
    public SchnorrSecret parse(Reader reader) {
        int secretKeyLength = reader.getInt();
        byte[] secretKey = reader.getBytes(secretKeyLength);
        int publicKeyLength = reader.getInt();
        byte[] publicKey = reader.getBytes(publicKeyLength);

        return new SchnorrSecret(secretKey, publicKey);
    }
}
