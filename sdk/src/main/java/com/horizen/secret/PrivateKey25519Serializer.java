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
    public void serialize(PrivateKey25519 secret, Writer writer) {
        writer.putBytes(secret.bytes());
    }

    @Override
    public PrivateKey25519 parse(Reader reader) {
        return PrivateKey25519.parseBytes(reader.getBytes(reader.remaining()));
    }
}
