package com.horizen.secret;

import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

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
        byte[] privateKeyBytes = Checker.readBytes(reader, PrivateKey25519.PRIVATE_KEY_LENGTH, "private key bytes");
        byte[] publicKeyBytes = Checker.readBytes(reader, PrivateKey25519.PUBLIC_KEY_LENGTH, "public key bytes");

        return new PrivateKey25519(privateKeyBytes, publicKeyBytes);
    }
}
