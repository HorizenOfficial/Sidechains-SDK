package com.horizen.secret;

import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class PrivateKey25519Serializer implements SecretSerializer<PrivateKey25519>
{
    private static PrivateKey25519Serializer serializer;

    static {
        serializer = new PrivateKey25519Serializer();
    }

    private PrivateKey25519Serializer() {
        super();

    }

    public static PrivateKey25519Serializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(PrivateKey25519 secret, Writer writer)
    {
        writer.putBytes(secret.privateKeyBytes);
        writer.putBytes(secret.publicKeyBytes);
    }

    @Override
    public PrivateKey25519 parse(Reader reader) {
        byte[] privateKeyBytes = reader.getBytes(PrivateKey25519.PRIVATE_KEY_LENGTH);
        byte[] publicKeyBytes = reader.getBytes(PrivateKey25519.PUBLIC_KEY_LENGTH);

        return new PrivateKey25519(privateKeyBytes, publicKeyBytes);
    }
}
