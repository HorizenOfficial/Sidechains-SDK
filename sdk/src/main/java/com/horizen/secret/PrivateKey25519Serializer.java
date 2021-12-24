package com.horizen.secret;

import com.google.common.primitives.Bytes;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;
import scorex.crypto.signatures.Curve25519;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.util.Arrays;

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
        writer.putBytes(secret._privateKeyBytes);
        writer.putBytes(secret._publicKeyBytes);
    }

    @Override
    public PrivateKey25519 parse(Reader reader) {
        byte[] privateKeyBytes = reader.getBytes(PrivateKey25519.KEY_LENGTH);
        byte[] publicKeyBytes = reader.getBytes(PrivateKey25519.KEY_LENGTH);

        return new PrivateKey25519(privateKeyBytes, publicKeyBytes);
    }
}
