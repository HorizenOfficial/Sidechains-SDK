package com.horizen.secret;

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
        byte[] secretKey = reader.getBytes(SchnorrSecret.SECRET_KEY_LENGTH);
        byte[] publicKey = reader.getBytes(SchnorrSecret.PUBLIC_KEY_LENGTH);

        SchnorrSecret schnorrSecret = new SchnorrSecret(secretKey, publicKey);

        // Considering that isPublicKeyValid() is time-consuming operation and public key may not be valid only when
        // it was red from somewhere(in all cases it's generated from private key), key validation was put here.
        if(!schnorrSecret.isPublicKeyValid())
            throw new IllegalArgumentException("The public key is not corresponds to the secret key.");

        return schnorrSecret;
    }
}
