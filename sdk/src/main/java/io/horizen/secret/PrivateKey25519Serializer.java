package io.horizen.secret;

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
        byte[] privateKeyBytes = reader.getBytes(PrivateKey25519.PRIVATE_KEY_LENGTH);
        byte[] publicKeyBytes = reader.getBytes(PrivateKey25519.PUBLIC_KEY_LENGTH);

        // Considering that isPublicKeyValid() is time-consuming operation and public key may not be valid only when
        // it was red from somewhere(in all cases it's generated from private key), key validation was put here.
        PrivateKey25519 privateKey25519 = new PrivateKey25519(privateKeyBytes, publicKeyBytes);
        if (!privateKey25519.isPublicKeyValid()) {
            throw new IllegalArgumentException("The public key is not corresponds to the secret key.");
        }

        return privateKey25519;
    }
}
